package com.ginogipsy.sanmartino.notifications.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema speculare a SagaEvent del saga-orchestrator. Tenuto separato per
 * lasciare al consumer la liberta' di evolvere indipendentemente. In un
 * portfolio piu' maturo si pubblica uno shared module schema (es. avro
 * + schema registry) ma per ora una copia e' sufficiente.
 */
public record SagaEvent(
        String schemaVersion,
        UUID sagaId,
        String sagaType,
        String status,
        UUID eventId,
        List<UUID> standIds,
        String message,
        Instant occurredAt
) {
}
