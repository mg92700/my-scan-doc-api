package com.scan.api.domain.model;

import java.util.UUID;

/**
 * Événement domaine émis lors de la création d'un document.
 * Sérialisé en JSON dans la table outbox par OutboxPort.
 */
public record DocumentCreatedEvent(
        UUID id,
        String name,
        String s3Key,
        String contentType,
        long size
) {}
