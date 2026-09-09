package com.ginogipsy.sanmartino.saga.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        // Il callback gira sul thread I/O del producer, che non ha l'MDC della richiesta:
        // senza questa copia le due righe qui sotto sarebbero le uniche non correlabili
        // della catena, proprio nel punto di passaggio da HTTP a Kafka. Si copia l'intera
        // mappa e non la singola chiave perche' il nome della chiave e' configurabile in
        // common-observability (sanmartino.observability.correlation.mdc-key).
        Map<String, String> context = MDC.getCopyOfContextMap();
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> withContext(context, () -> {
            if (ex != null) {
                log.error("Failed to publish saga event {}: {}", event.sagaId(), ex.getMessage());
            } else {
                log.info("Published saga event {} (status={}) to {}", event.sagaId(), event.status(), topic);
            }
        }));
    }

    /**
     * Esegue {@code action} con l'MDC catturato al momento della {@code send}, ripristinando
     * poi quello che il thread aveva prima. Il ripristino serve perche' il thread del
     * producer e' condiviso fra tutte le pubblicazioni: lasciarci dentro un correlation id
     * lo attribuirebbe alla saga successiva.
     */
    static void withContext(Map<String, String> context, Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        apply(context);
        try {
            action.run();
        } finally {
            apply(previous);
        }
    }

    private static void apply(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
