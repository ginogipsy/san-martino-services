package com.ginogipsy.sanmartino.observability.audit;

/**
 * Interfaccia per la pubblicazione asincrona di eventi di audit.
 */
public interface AuditPublisher {

    /**
     * Pubblica un evento di audit.
     *
     * @param event l'evento da pubblicare
     */
    void publish(AuditEvent event);
}
