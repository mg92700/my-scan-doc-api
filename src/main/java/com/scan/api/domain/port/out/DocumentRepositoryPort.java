package com.scan.api.domain.port.out;

import com.scan.api.domain.model.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (driven port) — abstraction de la persistance des documents.
 * Le domaine ne connaît PAS JPA. L'implémentation concrète (JPA) est dans l'infra.
 */
public interface DocumentRepositoryPort {

    /**
     * Persiste immédiatement le document.
     *
     * @throws com.scan.api.domain.model.DocumentAlreadyExistsException
     *         si un document de même sha256 existe déjà
     */
    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findBySha256(String sha256);

    /** Documents actifs ({@code archived=false}) ou archivés ({@code archived=true}), du plus récent au plus ancien. */
    List<Document> findAllByArchived(boolean archived);

    void deleteById(UUID id);
}
