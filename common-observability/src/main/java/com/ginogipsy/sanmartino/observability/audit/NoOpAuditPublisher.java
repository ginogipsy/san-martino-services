package com.ginogipsy.sanmartino.observability.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementazione di fallback usata quando Kafka non è configurato o l'audit è disabilitato.
 */
public class NoOpAuditPublisher implements AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpAuditPublisher.class);

    @Override
    public void publish(AuditEvent event) {
        log.debug("NoOpAuditPublisher: audit event ignorato (nessun publisher attivo): action={}, serviceId={}",
                event.action(), event.serviceId());
    }
}
