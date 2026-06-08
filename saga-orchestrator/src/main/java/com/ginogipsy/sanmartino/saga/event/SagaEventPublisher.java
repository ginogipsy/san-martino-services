package com.ginogipsy.sanmartino.saga.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SagaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SagaEventPublisher.class);

    private final KafkaTemplate<String, SagaEvent> kafkaTemplate;
    private final String topic;

    public SagaEventPublisher(
            KafkaTemplate<String, SagaEvent> kafkaTemplate,
            @Value("${sanmartino.kafka.topics.saga-events:sanmartino.saga.events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(SagaEvent event) {
        String key = event.sagaId().toString();
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish saga event {}: {}", event.sagaId(), ex.getMessage());
            } else {
                log.info("Published saga event {} (status={}) to {}", event.sagaId(), event.status(), topic);
            }
        });
    }
}
