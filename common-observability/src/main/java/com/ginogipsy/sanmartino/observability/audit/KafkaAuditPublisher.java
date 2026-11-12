package com.ginogipsy.sanmartino.observability.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Pubblica eventi di audit in modo asincrono su un topic Kafka dedicato.
 */
public class KafkaAuditPublisher implements AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaAuditPublisher(KafkaTemplate<String, Object> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(AuditEvent event) {
        try {
            kafkaTemplate.send(topic, event.serviceId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Impossibile inviare evento di audit {} su topic {}: {}",
                                    event.id(), topic, ex.getMessage());
                        } else {
                            log.debug("Evento di audit {} [{}] inviato su topic {}",
                                    event.id(), event.action(), topic);
                        }
                    });
        } catch (Exception ex) {
            log.error("Errore imprevisto durante l'invio dell'evento di audit {}: {}",
                    event.id(), ex.getMessage(), ex);
        }
    }
}
