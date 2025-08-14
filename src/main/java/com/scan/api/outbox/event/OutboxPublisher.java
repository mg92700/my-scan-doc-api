package com.scan.api.outbox.event;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final KafkaTemplate<String, Object> kafka;
    @Value("${app.kafka.topic-doc-created:documents.created}")
    private String topicDocCreated;

    /**
     * Publie de façon synchrone et retourne les métadonnées Kafka.
     */
    public RecordMetadata publishSync(String type, String key, String payload) throws Exception {
        String topic = switch (type) {
            case "DocumentCreated" -> topicDocCreated;
            default -> throw new IllegalArgumentException("Unknown outbox type: " + type);
        };
        SendResult<String, Object> result = kafka.send(topic, key, payload).get();
        return result.getRecordMetadata();
    }
}
