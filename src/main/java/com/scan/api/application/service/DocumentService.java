package com.scan.api.application.service;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentCreatedEvent;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import com.scan.api.domain.port.out.OutboxPort;
import com.scan.api.domain.port.out.StoragePort;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implémentation du use case DocumentUseCase.
 *
 * Dépend UNIQUEMENT des ports (interfaces) — aucun import JPA, S3, Kafka.
 * Le wiring Spring est fait dans infrastructure/config/BeanConfig.java.
 *
 * Note : @Transactional est la seule annotation Spring autorisée ici.
 * Elle ne crée aucun couplage métier, elle exprime une règle d'invariant.
 */
public class DocumentService implements DocumentUseCase {

    private static final String PDF_MAGIC = "%PDF-";
    private static final long MAX_SIZE = 20 * 1024 * 1024L; // 20 MB

    private final DocumentRepositoryPort repository;
    private final StoragePort storage;
    private final OutboxPort outbox;

    public DocumentService(DocumentRepositoryPort repository,
                           StoragePort storage,
                           OutboxPort outbox) {
        this.repository = repository;
        this.storage = storage;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public Document savePdf(String originalFilename, String contentType,
                            InputStream in, long size) throws IOException {

        // --- Validation ---
        if (size <= 0 || size > MAX_SIZE)
            throw new IllegalArgumentException("Taille invalide (max 20MB)");

        byte[] head = in.readNBytes(5);
        if (!new String(head).startsWith(PDF_MAGIC))
            throw new IllegalArgumentException("Pas un PDF valide");

        byte[] rest = in.readAllBytes();
        byte[] all = new byte[head.length + rest.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(rest, 0, all, head.length, rest.length);

        // --- Dédoublonnage ---
        String sha = sha256(all);
        var existing = repository.findBySha256(sha);
        if (existing.isPresent()) return existing.get();

        // --- Stockage objet ---
        String key = storage.store(
                new ByteArrayInputStream(all), all.length,
                MediaType.APPLICATION_PDF_VALUE, "pdf");

        // --- Persistance métadonnées ---
        Document doc = Document.builder()
                .originalFilename(originalFilename == null ? "document.pdf" : originalFilename)
                .size(all.length)
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .sha256(sha)
                .s3Key(key)
                .createdAt(Instant.now())
                .build();
        doc = repository.save(doc);

        // --- Outbox (même transaction) ---
        outbox.enqueue(doc.getId(), "DocumentCreated",
                new DocumentCreatedEvent(
                        doc.getId(),
                        doc.getOriginalFilename(),
                        doc.getS3Key(),
                        doc.getContentType(),
                        doc.getSize()
                ));

        return doc;
    }

    @Override
    public DownloadResult download(UUID id) {
        Document doc = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        byte[] bytes = storage.get(doc.getS3Key());
        return new DownloadResult(doc.getOriginalFilename(), doc.getContentType(), bytes);
    }

    // --- Utilitaires privés ---

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
