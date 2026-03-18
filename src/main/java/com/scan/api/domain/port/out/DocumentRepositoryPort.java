package com.scan.api.domain.port.out;

import com.scan.api.domain.model.Document;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant (driven port) — abstraction de la persistance des documents.
 * Le domaine ne connaît PAS JPA. L'implémentation concrète (JPA) est dans l'infra.
 */
public interface DocumentRepositoryPort {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findBySha256(String sha256);
}
