package com.ginogipsy.sanmartino.saga.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento di dominio pubblicato su Kafka quando una saga termina (COMPLETED o COMPENSATED).
 * Schema deliberatamente piatto e versionato per essere stabile contro modifiche del DB interno.
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
    public static final String SCHEMA_VERSION = "v1";

    public static SagaEvent completed(UUID sagaId, String type, UUID eventId, List<UUID> standIds, Instant at) {
        return new SagaEvent(SCHEMA_VERSION, sagaId, type, "COMPLETED", eventId, standIds, null, at);
    }

    public static SagaEvent compensated(UUID sagaId, String type, UUID eventId, String message, Instant at) {
        return new SagaEvent(SCHEMA_VERSION, sagaId, type, "COMPENSATED", eventId, List.of(), message, at);
    }
}
