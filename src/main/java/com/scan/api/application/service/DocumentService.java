package com.scan.api.application.service;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.StoragePort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implémentation du use case DocumentUseCase.
 *
 * Dépend UNIQUEMENT des ports (interfaces) — aucun import JPA, S3 ni Spring.
 * Le wiring Spring est fait dans infrastructure/config/BeanConfig.java.
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

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    static final int MAX_SIZE = 20 * 1024 * 1024; // 20 MB
    static final int MAX_FILENAME_LENGTH = 255;
    static final String DEFAULT_FILENAME = "document.pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final DocumentRepositoryPort repository;
    private final StoragePort storage;

    public DocumentService(DocumentRepositoryPort repository,
                           StoragePort storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    public Document savePdf(String originalFilename, String contentType,
                            InputStream in, long size) throws IOException {

        // --- Validation ---
        if (size <= 0 || size > MAX_SIZE)
            throw new IllegalArgumentException("Taille invalide (max 20MB)");

        // La taille déclarée par le client n'est pas fiable : on borne la lecture réelle.
        byte[] all = in.readNBytes(MAX_SIZE + 1);
        if (all.length == 0 || all.length > MAX_SIZE)
            throw new IllegalArgumentException("Taille invalide (max 20MB)");
        if (!startsWith(all, PDF_MAGIC))
            throw new IllegalArgumentException("Pas un PDF valide");

        // --- Dédoublonnage ---
        String sha = sha256(all);
        var existing = repository.findBySha256(sha);
        if (existing.isPresent()) return existing.get();

        // --- Stockage objet ---
        String key = storage.store(
                new ByteArrayInputStream(all), all.length, PDF_CONTENT_TYPE, "pdf");

        // --- Persistance métadonnées (avec compensation) ---
        Document doc = Document.builder()
                .originalFilename(sanitizeFilename(originalFilename))
                .size(all.length)
                .contentType(PDF_CONTENT_TYPE)
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
        return new DownloadResult(doc.getOriginalFilename(), doc.getContentType(), bytes);
    }

    // --- Utilitaires ---

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

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
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
