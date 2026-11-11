package com.ginogipsy.sanmartino.observability.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Risolve l'utente autenticato a partire dal {@link SecurityContextHolder} di Spring Security.
 * Estrae userId e username supportando sia token JWT di Keycloak che autenticazioni standard.
 */
public class SecurityContextAuditUserResolver implements AuditUserResolver {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextAuditUserResolver.class);

    @Override
    public AuditUser resolveUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
                return AuditUser.ANONYMOUS;
            }

            String userId = auth.getName();
            String username = auth.getName();

            // Ispeziona il principal per recuperare i claim standard JWT (preferred_username, sub)
            Object principal = auth.getPrincipal();
            if (principal != null) {
                Map<String, Object> claims = extractClaims(principal);
                if (claims != null && !claims.isEmpty()) {
                    if (claims.get("sub") instanceof String sub && !sub.isBlank()) {
                        userId = sub;
                    }
                    if (claims.get("preferred_username") instanceof String pref && !pref.isBlank()) {
                        username = pref;
                    } else if (claims.get("username") instanceof String u && !u.isBlank()) {
                        username = u;
                    }
                }
            }

            return new AuditUser(userId, username);
        } catch (Exception ex) {
            log.trace("Impossibile estrarre l'utente autenticato dal SecurityContext: {}", ex.getMessage());
            return AuditUser.ANONYMOUS;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractClaims(Object principal) {
        try {
            Method getClaims = principal.getClass().getMethod("getClaims");
            Object res = getClaims.invoke(principal);
            if (res instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (NoSuchMethodException ignored) {
            // Non è un oggetto Jwt o con getClaims()
        } catch (Exception e) {
            log.trace("Errore durante l'estrazione dei claim dal principal: {}", e.getMessage());
        }
        return Map.of();
    }
}
