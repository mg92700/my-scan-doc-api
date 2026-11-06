package com.scan.api.infrastructure.adapter.out.messaging;

import tools.jackson.databind.ObjectMapper;
import com.scan.api.domain.port.out.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter sortant — implémente OutboxPort en persistant les événements via JDBC.
 * La lecture + publication vers Kafka reste dans OutboxRelay (scheduled job).
 */
@Component
@RequiredArgsConstructor
public class OutboxJdbcAdapter implements OutboxPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(UUID aggregateId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            jdbc.update("""
                INSERT INTO outbox(
                    aggregate_id, type, payload, created_at, published, attempts
                )
                VALUES (
                    ?, ?, ?::jsonb, now(), false, 0
                )
            """, aggregateId, type, json);
        } catch (Exception e) {
            throw new RuntimeException("Outbox enqueue failed", e);
        }
    }
}
