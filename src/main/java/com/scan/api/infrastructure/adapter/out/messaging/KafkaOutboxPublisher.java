package com.scan.api.infrastructure.adapter.out.messaging;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publie les événements outbox vers Kafka.
 * Interne au package messaging — non exposé en dehors de l'infra.
 */
@Component
@RequiredArgsConstructor
class KafkaOutboxPublisher {

    private final KafkaTemplate<String, String> kafka;

    @Value("${app.kafka.topic-doc-created:documents.created}")
    private String topicDocCreated;

    RecordMetadata publishSync(String type, String key, String payload) throws Exception {
        String topic = switch (type) {
            case "DocumentCreated" -> topicDocCreated;
            default -> throw new IllegalArgumentException("Unknown outbox type: " + type);
        };
        SendResult<String, String> result = kafka.send(topic, key, payload).get();
        return result.getRecordMetadata();
    }
}
