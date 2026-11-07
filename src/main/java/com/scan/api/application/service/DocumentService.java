package com.scan.api.application.service;

import com.scan.api.domain.model.ConversionException;
import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.ImageToPdfPort;
import com.scan.api.domain.port.out.StoragePort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Implémentation du use case DocumentUseCase.
 *
 * Dépend UNIQUEMENT des ports (interfaces) — aucun import JPA, S3 ni Spring.
 * Le wiring Spring est fait dans infrastructure/config/BeanConfig.java.
 *
 * Formats reçus (reconnus par le contenu, jamais par le Content-Type déclaré) :
 *  - PDF : stocké tel quel ;
 *  - image (JPEG, PNG, GIF, BMP, TIFF, WebP, HEIC…) : convertie en PDF ;
 *    si la conversion est impossible, l'image d'origine est archivée octet pour octet
 *    dans un ZIP (compression sans perte) ;
 *  - tout autre format : refusé.
 * La déduplication (SHA-256) porte sur le fichier reçu, pas sur le fichier stocké.
 *
 * Cohérence S3 / base sans transaction distribuée :
 *  1. on écrit l'objet dans S3 ;
 *  2. on persiste les métadonnées (commit immédiat) ;
 *  3. si l'étape 2 échoue, on supprime l'objet S3 (compensation).
 * Volontairement pas de @Transactional : on ne garde pas une connexion
 * base ouverte pendant l'appel réseau vers S3.
 */
public class DocumentService implements DocumentUseCase {

    private static final System.Logger LOG = System.getLogger(DocumentService.class.getName());

    static final int MAX_SIZE = 20 * 1024 * 1024; // 20 MB
    static final int MAX_FILENAME_LENGTH = 255;
    static final String DEFAULT_FILENAME = "document.pdf";
    static final String PDF_CONTENT_TYPE = "application/pdf";
    static final String ZIP_CONTENT_TYPE = "application/zip";

    private final DocumentRepositoryPort repository;
    private final StoragePort storage;
    private final ImageToPdfPort imageConverter;

    /** Fichier prêt à être stocké : contenu, type et extension. */
    private record StoredFile(byte[] bytes, String contentType, String extension) {}

    public DocumentService(DocumentRepositoryPort repository,
                           StoragePort storage,
                           ImageToPdfPort imageConverter) {
        this.repository = repository;
        this.storage = storage;
        this.imageConverter = imageConverter;
    }

    @Override
    public Document save(String originalFilename, String contentType,
                         InputStream in, long size) throws IOException {

        // --- Validation ---
        if (size <= 0 || size > MAX_SIZE)
            throw new IllegalArgumentException("Taille invalide (max 20MB)");

        // La taille déclarée par le client n'est pas fiable : on borne la lecture réelle.
        byte[] source = in.readNBytes(MAX_SIZE + 1);
        if (source.length == 0 || source.length > MAX_SIZE)
            throw new IllegalArgumentException("Taille invalide (max 20MB)");

        boolean pdf = FileSignature.isPdf(source);
        if (!pdf && !FileSignature.isImage(source))
            throw new IllegalArgumentException(
                    "Format non supporté : PDF ou image (JPEG, PNG, GIF, BMP, TIFF, WebP, HEIC) attendu");

        // --- Dédoublonnage (sur le fichier reçu) ---
        String sha = sha256(source);
        var existing = repository.findBySha256(sha);
        if (existing.isPresent()) return existing.get();

        // --- Préparation : PDF tel quel, image convertie en PDF, sinon archivée en ZIP ---
        StoredFile file = pdf
                ? new StoredFile(source, PDF_CONTENT_TYPE, "pdf")
                : convertOrArchive(source, originalFilename);

        // --- Stockage objet ---
        String key = storage.store(
                new ByteArrayInputStream(file.bytes()), file.bytes().length, file.contentType(), file.extension());

        // --- Persistance métadonnées (avec compensation) ---
        Document doc = Document.builder()
                .originalFilename(sanitizeFilename(originalFilename))
                .size(file.bytes().length)
                .contentType(file.contentType())
                .sha256(sha)
                .s3Key(key)
                .createdAt(Instant.now())
                .build();
        try {
            return repository.save(doc);
        } catch (DocumentAlreadyExistsException e) {
            // Upload concurrent du même fichier : l'autre a gagné, on rend le sien.
            deleteQuietly(key);
            return repository.findBySha256(sha).orElseThrow(() -> e);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Override
    public DownloadResult download(UUID id) {
        Document doc = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        byte[] bytes = storage.get(doc.getS3Key());
        return new DownloadResult(
                downloadName(doc.getOriginalFilename(), doc.getContentType()), doc.getContentType(), bytes);
    }

    @Override
    public List<Document> list(boolean archived) {
        return repository.findAllByArchived(archived);
    }

    @Override
    public Document archive(UUID id) {
        Document doc = find(id);
        if (doc.getArchivedAt() == null) {
            doc.setArchivedAt(Instant.now());
            return repository.save(doc);
        }
        return doc;
    }

    @Override
    public Document unarchive(UUID id) {
        Document doc = find(id);
        if (doc.getArchivedAt() != null) {
            doc.setArchivedAt(null);
            return repository.save(doc);
        }
        return doc;
    }

    @Override
    public void delete(UUID id) {
        Document doc = find(id);
        // Base d'abord : un objet orphelin dans le stockage est inoffensif, une ligne sans fichier non.
        repository.deleteById(id);
        deleteQuietly(doc.getS3Key());
    }

    private Document find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    // --- Conversion / archivage ---

    private StoredFile convertOrArchive(byte[] image, String originalFilename) {
        try {
            return new StoredFile(imageConverter.toPdf(image), PDF_CONTENT_TYPE, "pdf");
        } catch (ConversionException e) {
            LOG.log(System.Logger.Level.INFO,
                    "Conversion en PDF impossible ({0}) : image archivée telle quelle dans un ZIP", e.getMessage());
            return new StoredFile(zip(entryName(originalFilename), image), ZIP_CONTENT_TYPE, "zip");
        }
    }

    /** Archive ZIP à une seule entrée : le contenu d'origine, octet pour octet. */
    private static byte[] zip(String entryName, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // impossible en mémoire
        }
        return out.toByteArray();
    }

    // --- Utilitaires ---

    /**
     * Nom de l'entrée dans l'archive : sans dossier, sans « .. », caractères limités à
     * {@code A-Za-z0-9._ -}. Évite qu'un nom piégé n'écrive hors du dossier à l'extraction (« zip slip »).
     */
    static String entryName(String filename) {
        String n = filename == null ? "" : filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("^\\.+", "");
        return n.isBlank() ? "image" : n;
    }

    /** Nom proposé au téléchargement : l'extension reflète ce qui est réellement stocké. */
    static String downloadName(String originalFilename, String contentType) {
        String base = (originalFilename == null || originalFilename.isBlank()) ? "document" : originalFilename;
        if (PDF_CONTENT_TYPE.equals(contentType)) {
            if (base.toLowerCase().endsWith(".pdf")) return base;
            int dot = base.lastIndexOf('.');
            return (dot > 0 ? base.substring(0, dot) : base) + ".pdf";
        }
        if (ZIP_CONTENT_TYPE.equals(contentType)) return base + ".zip";
        return base;
    }

    /**
     * Garde uniquement le nom de fichier (certains navigateurs envoient le chemin complet),
     * retire les caractères de contrôle et borne la longueur à celle de la colonne.
     */
    static String sanitizeFilename(String name) {
        if (name == null) return DEFAULT_FILENAME;
        String n = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        n = n.replaceAll("\\p{Cntrl}", "").strip();
        if (n.isEmpty()) return DEFAULT_FILENAME;
        if (n.length() > MAX_FILENAME_LENGTH) {
            int dot = n.lastIndexOf('.');
            String ext = (dot > 0 && n.length() - dot <= 10) ? n.substring(dot) : "";
            n = n.substring(0, MAX_FILENAME_LENGTH - ext.length()) + ext;
        }
        return n;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Compensation impossible, objet orphelin dans le stockage: " + key, ex);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e); // garanti par la JVM
        }
    }
}
