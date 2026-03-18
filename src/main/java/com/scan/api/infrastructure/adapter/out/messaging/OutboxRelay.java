package com.scan.api.infrastructure.adapter.out.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled job — lit les événements non publiés de l'outbox et les envoie à Kafka.
 * Complètement dans l'infra : le domaine ne sait pas qu'il existe.
 */
@Component
@Slf4j
public class OutboxRelay {

    private final OutboxJdbcRepository repo;
    private final KafkaOutboxPublisher publisher;
    private final PlatformTransactionManager txManager;

    @Value("${app.outbox.relay.enabled:true}")
    private boolean enabled;

    @Value("${app.outbox.relay.batch-size:500}")
    private int batchSize;

    public OutboxRelay(OutboxJdbcRepository repo,
                       KafkaOutboxPublisher publisher,
                       PlatformTransactionManager txManager) {
        this.repo = repo;
        this.publisher = publisher;
        this.txManager = txManager;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:1000}")
    public void run() {
        if (!enabled) return;

        var tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            var rows = repo.lockBatchForPublish(batchSize);
            if (rows.isEmpty()) return null;

            for (var row : rows) {
                try {
                    RecordMetadata meta = publisher.publishSync(
                            row.type(), row.aggregateId().toString(), row.payload());
                    repo.markPublished(row.id());
                    log.debug("Outbox id={} published → topic={} partition={} offset={}",
                            row.id(), meta.topic(), meta.partition(), meta.offset());
                } catch (Exception ex) {
                    repo.markFailed(row.id(), ex.getMessage());
                    log.warn("Outbox id={} failed: {}", row.id(), ex.toString());
                }
            }
            return null;
        });
    }
}
