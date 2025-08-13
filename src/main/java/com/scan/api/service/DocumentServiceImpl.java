package com.scan.api.service;



import com.scan.api.domain.Document;

import com.scan.api.repository.DocumentRepository;
import com.scan.api.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final String PDF_MAGIC = "%PDF-";

    private final DocumentRepository repository;
    private final StorageService storage;

    @Override
    public Document savePdf(String originalFilename, String contentType, InputStream in, long size) throws IOException {
        if (size <= 0 || size > 20 * 1024 * 1024) throw new IllegalArgumentException("Taille invalide (max 20MB)");

        byte[] head = in.readNBytes(5);
        if (!new String(head).startsWith(PDF_MAGIC)) throw new IllegalArgumentException("Pas un PDF valide");
        byte[] rest = in.readAllBytes();
        byte[] all = new byte[head.length + rest.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(rest, 0, all, head.length, rest.length);

        String sha = sha256(all);
        var existing = repository.findBySha256(sha);
        if (existing.isPresent()) return existing.get();

        String key = storage.store(new ByteArrayInputStream(all), all.length,
                MediaType.APPLICATION_PDF_VALUE, "pdf");

        Document doc = Document.builder()
                .originalFilename(originalFilename == null ? "document.pdf" : originalFilename)
                .size(all.length)
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .sha256(sha)
                .s3Key(key)
                .createdAt(Instant.now())
                .build();

        return repository.save(doc);
    }

    @Override
    public DownloadResult download(UUID id) {
        Document doc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable: " + id));
        byte[] bytes = storage.get(doc.getS3Key());
        return new DownloadResult(doc.getOriginalFilename(), doc.getContentType(), bytes);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
