package com.ginogipsy.sanmartino.observability.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Risolve l'utente autenticato a partire dal {@link SecurityContextHolder} di Spring Security
 * (se presente sul classpath) oppure decodificando il claim JWT dall'header {@code Authorization}
 * della richiesta HTTP corrente nei microservizi interni privi di Spring Security.
 */
public class SecurityContextAuditUserResolver implements AuditUserResolver {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextAuditUserResolver.class);

    private static final boolean SPRING_SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContextHolder",
            SecurityContextAuditUserResolver.class.getClassLoader()
    );

    private static final Pattern SUB_PATTERN = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PREF_USER_PATTERN = Pattern.compile("\"preferred_username\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"");

    private static AuditUser resolveFromRequestAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return null;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String sub = extractJsonString(SUB_PATTERN, payloadJson);
        String username = extractJsonString(PREF_USER_PATTERN, payloadJson);
        if (username == null || username.isBlank()) {
            username = extractJsonString(USERNAME_PATTERN, payloadJson);
        }
        if ((sub == null || sub.isBlank()) && (username == null || username.isBlank())) {
            return null;
        }
        return new AuditUser(
                sub != null && !sub.isBlank() ? sub : username,
                username != null && !username.isBlank() ? username : sub
        );
    }

    private static String extractJsonString(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public AuditUser resolveUser() {
        if (SPRING_SECURITY_PRESENT) {
            try {
                AuditUser fromSecurity = SpringSecurityHelper.resolveFromSecurityContext();
                if (fromSecurity != null && fromSecurity != AuditUser.ANONYMOUS) {
                    return fromSecurity;
                }
            } catch (Throwable ex) {
                log.trace("Impossibile estrarre l'utente autenticato dal SecurityContext: {}", ex.getMessage());
            }
        }

        try {
            AuditUser fromHeader = resolveFromRequestAuthorizationHeader();
            if (fromHeader != null) {
                return fromHeader;
            }
        } catch (Throwable ex) {
            log.trace("Impossibile estrarre l'utente autenticato dall'header Authorization: {}", ex.getMessage());
        }

        return AuditUser.ANONYMOUS;
    }

    /**
     * Isolato in una inner class separata per evitare {@link NoClassDefFoundError}
     * quando {@code spring-security-core} non è sul classpath del microservizio.
     */
    private static final class SpringSecurityHelper {

        static AuditUser resolveFromSecurityContext() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
                return AuditUser.ANONYMOUS;
            }

            String userId = auth.getName();
            String username = auth.getName();

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
}
