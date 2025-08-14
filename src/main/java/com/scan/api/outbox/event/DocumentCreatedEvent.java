package com.scan.api.outbox.event;

import java.util.UUID;

public record DocumentCreatedEvent(
        UUID id,
        String name,
        String s3Key,
        String contentType,
        long size
) {}
