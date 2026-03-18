package com.scan.api.domain.port.out;

import java.util.UUID;

/**
 * Port sortant — abstraction de l'outbox transactionnel.
 * Le domaine enqueue un événement sans savoir comment il sera publié (Kafka, SNS…).
 */
public interface OutboxPort {

    /**
     * Persiste l'événement dans la table outbox, dans la même transaction applicative.
     *
     * @param aggregateId identifiant de l'agrégat concerné (ex: document.id)
     * @param type        type d'événement (ex: "DocumentCreated")
     * @param payload     objet sérialisable représentant l'événement
     */
    void enqueue(UUID aggregateId, String type, Object payload);
}
