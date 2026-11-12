package com.ginogipsy.sanmartino.observability.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Evento immutabile di audit trail di business.
 *
 * @param id            identificativo univoco dell'evento (UUID)
 * @param serviceId     identificativo del microservizio sorgente (es. "stands-service", "events-service")
 * @param action        codice dell'operazione di business (es. "STAND:PRICE_UPDATE", "EVENT:CREATE")
 * @param userId        identificativo dell'utente che ha scatenato l'evento (es. sub/ID da JWT)
 * @param username      username dell'operatore (es. preferred_username da JWT)
 * @param resourceId    identificativo dell'entità o risorsa impattata (opzionale, es. standId)
 * @param description   descrizione testuale dell'operazione (anche valutata tramite SpEL)
 * @param correlationId correlation ID distribuito della richiesta
 * @param timestamp     istante temporale UTC in cui si è verificato l'evento
 * @param metadata      ulteriori dettagli strutturati (chiave/valore serializzabile in JSONB)
 */
public record AuditEvent(
        String id,
        String serviceId,
        String action,
        String userId,
        String username,
        String resourceId,
        String description,
        String correlationId,
        Instant timestamp,
        Map<String, Object> metadata
) {
    private static final java.util.regex.Pattern ACTION_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z0-9_]+:[A-Z0-9_]+$");

    public AuditEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Audit id cannot be null or blank");
        }
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId cannot be null or blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action cannot be null or blank");
        }
        if (!ACTION_PATTERN.matcher(action).matches()) {
            throw new IllegalArgumentException(
                    "Action format non valido: '" + action + "'. Il formato atteso è <DOMINIO>:<AZIONE> (es. STAND:CREATE, EVENT:UPDATE)");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
