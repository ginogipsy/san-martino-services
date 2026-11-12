package com.ginogipsy.sanmartino.observability.audit;

/**
 * Risolve l'identità dell'utente/operatore corrente per l'evento di audit.
 */
public interface AuditUserResolver {

    AuditUser resolveUser();

    record AuditUser(String userId, String username) {
        public static final AuditUser ANONYMOUS = new AuditUser("anonymous", "anonymous");
    }
}
