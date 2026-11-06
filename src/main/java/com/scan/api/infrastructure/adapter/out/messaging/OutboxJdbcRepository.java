package com.scan.api.infrastructure.adapter.out.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accès JDBC à la table outbox — interne au package messaging, non exposé au domaine.
 * Utilisé uniquement par OutboxRelay.
 */
@Repository
@RequiredArgsConstructor
class OutboxJdbcRepository {

    private final JdbcTemplate jdbc;

    List<OutboxRow> lockBatchForPublish(int limit) {
        return jdbc.query("""
            SELECT id, aggregate_id, type, payload::text, attempts
            FROM outbox
            WHERE published = false
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        """, (rs, i) -> new OutboxRow(
                rs.getLong("id"),
                (UUID) rs.getObject("aggregate_id"),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getInt("attempts")
        ), limit);
    }

    void markPublished(long id) {
        jdbc.update("""
            UPDATE outbox SET published=true, published_at=?, last_error=NULL WHERE id=?
        """, java.sql.Timestamp.from(Instant.now()), id);
    }

    void markFailed(long id, String error) {
        // la colonne last_error est un varchar(255) : au-delà, l'UPDATE échouerait et ferait rollback du lot
        String msg = error == null ? "" : (error.length() > 255 ? error.substring(0, 255) : error);
        jdbc.update("""
            UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?
        """, msg, id);
    }

    record OutboxRow(long id, UUID aggregateId, String type, String payload, int attempts) {}
}
