package com.scan.api.outbox.event;


import com.scan.api.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// ...

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository repo;
    private final OutboxPublisher publisher;

    // ⬇️ remplace DataSourceTransactionManager par PlatformTransactionManager
    private final PlatformTransactionManager txManager;

    @Value("${app.outbox.relay.enabled:true}")
    private boolean enabled;
    @Value("${app.outbox.relay.batch-size:500}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:1000}")
    public void run() {
        if (!enabled) return;

        var tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            var rows = repo.lockBatchForPublish(batchSize);
            if (rows.isEmpty()) return null;

            for (var row : rows) {
                try {
                    var meta = publisherPublishSync(row);
                    repo.markPublished(row.id());
                    log.debug("Outbox id={} published, topic={}, partition={}, offset={}",
                            row.id(), meta.topic(), meta.partition(), meta.offset());
                } catch (Exception ex) {
                    repo.markFailed(row.id(), ex.getMessage());
                    log.warn("Outbox id={} failed: {}", row.id(), ex.toString());
                }
            }
            return null;
        });
    }

    private RecordMetadata publisherPublishSync(OutboxRepository.OutboxRow row) throws Exception {
        return publisher.publishSync(row.type(), row.aggregateId().toString(), row.payload());
    }
}
