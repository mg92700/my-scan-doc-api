package com.scan.api.infrastructure.adapter.out.persistence;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter sortant — implémente le port DocumentRepositoryPort via JPA.
 * Responsable du mapping bidirectionnel entre Document (domaine) et DocumentJpaEntity (JPA).
 */
@Component
@RequiredArgsConstructor
public class DocumentJpaAdapter implements DocumentRepositoryPort {

    private final DocumentJpaRepository jpaRepository;

    @Override
    public Document save(Document document) {
        DocumentJpaEntity entity = toEntity(document);
        DocumentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Document> findBySha256(String sha256) {
        return jpaRepository.findBySha256(sha256).map(this::toDomain);
    }

    // --- Mappers privés ---

    private DocumentJpaEntity toEntity(Document d) {
        return DocumentJpaEntity.builder()
                .id(d.getId())
                .originalFilename(d.getOriginalFilename())
                .size(d.getSize())
                .contentType(d.getContentType())
                .sha256(d.getSha256())
                .s3Key(d.getS3Key())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private Document toDomain(DocumentJpaEntity e) {
        return Document.builder()
                .id(e.getId())
                .originalFilename(e.getOriginalFilename())
                .size(e.getSize())
                .contentType(e.getContentType())
                .sha256(e.getSha256())
                .s3Key(e.getS3Key())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
