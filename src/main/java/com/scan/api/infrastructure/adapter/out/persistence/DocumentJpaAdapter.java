package com.scan.api.infrastructure.adapter.out.persistence;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentAlreadyExistsException;
import com.scan.api.domain.model.DocumentPage;
import com.scan.api.domain.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Locale;
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
        try {
            // flush immédiat : une violation de contrainte remonte ici, pas au commit
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (document.getSha256() != null
                    && jpaRepository.findBySha256(document.getSha256()).isPresent()) {
                throw new DocumentAlreadyExistsException(document.getSha256(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Document> findBySha256(String sha256) {
        return jpaRepository.findBySha256(sha256).map(this::toDomain);
    }

    @Override
    public DocumentPage search(boolean archived, String query, int offset, int limit) {
        var result = jpaRepository.search(archived, likePattern(query), offset, limit);
        return new DocumentPage(result.items().stream().map(this::toDomain).toList(), result.total());
    }

    /**
     * Motif « contient » insensible à la casse. Les caractères joker du texte saisi ({@code %}, {@code _})
     * sont neutralisés avec {@code !} : taper « 100% » cherche bien « 100% » et pas « tout ».
     */
    static String likePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
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
                .archivedAt(d.getArchivedAt())
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
                .archivedAt(e.getArchivedAt())
                .build();
    }
}
