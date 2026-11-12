package com.ginogipsy.sanmartino.audit.consumer;

import com.ginogipsy.sanmartino.audit.entity.AuditLogEntity;
import com.ginogipsy.sanmartino.audit.repository.AuditLogRepository;
import com.ginogipsy.sanmartino.observability.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @KafkaListener(
            topics = "${sanmartino.observability.audit.topic:sanmartino.audit.events}",
            groupId = "${spring.kafka.consumer.group-id:audit-service}"
    )
    @Transactional
    public void onAuditEvent(AuditEvent event) {
        if (event == null) {
            log.warn("Ricevuto evento di audit nullo, scartato");
            return;
        }

        log.debug("Ricevuto evento di audit da Kafka: id={}, action={}, service={}",
                event.id(), event.action(), event.serviceId());

        AuditLogEntity entity = new AuditLogEntity(
                event.id(),
                event.serviceId(),
                event.action(),
                event.userId(),
                event.username(),
                event.resourceId(),
                event.description(),
                event.correlationId(),
                event.timestamp(),
                event.metadata()
        );

        auditLogRepository.save(entity);
        log.info("Salvato audit log: id={}, action={}, service={}", entity.getId(), entity.getAction(), entity.getServiceId());
    }
}
