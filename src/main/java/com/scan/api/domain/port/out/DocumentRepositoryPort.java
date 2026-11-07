package com.scan.api.domain.port.out;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentPage;

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

    /**
     * Cherche les documents actifs ({@code archived=false}) ou archivés ({@code archived=true}) dont le nom
     * contient {@code query} (sans tenir compte de la casse ; vide = tous), du plus récent au plus ancien.
     *
     * @param offset rang du premier résultat (0 = début)
     * @param limit  nombre maximal de résultats
     */
    DocumentPage search(boolean archived, String query, int offset, int limit);

    void deleteById(UUID id);
}
