package com.scan.api.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

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



    public List<OutboxRow> lockBatchForPublish(int limit) {
        return jdbc.query("""
           SELECT id, aggregate_id, type, payload::text, attempts
           FROM outbox
           WHERE published = false
           ORDER BY id
           FOR UPDATE SKIP LOCKED
           LIMIT ?
        """, (rs, i) -> new OutboxRow(
                rs.getLong("id"),
                (java.util.UUID) rs.getObject("aggregate_id"),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getInt("attempts")
        ), limit);
    }

    public void markPublished(long id) {
        jdbc.update("""
          UPDATE outbox SET published=true, published_at=?, last_error=NULL WHERE id=?
        """, java.sql.Timestamp.from(Instant.now()), id);
    }

    public void markFailed(long id, String error) {
        jdbc.update("""
          UPDATE outbox
          SET attempts = attempts + 1, last_error = ?
          WHERE id = ?
        """, error == null ? "" : (error.length() > 2000 ? error.substring(0,2000) : error), id);
    }

    public record OutboxRow(long id, java.util.UUID aggregateId, String type, String payload, int attempts) {}
}