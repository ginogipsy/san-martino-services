package com.ginogipsy.sanmartino.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assegna a ogni richiesta un correlation id e lo rende visibile a tutti i pezzi
 * che devono seguirla: MDC (quindi i log, come campo JSON), header di risposta e
 * header della richiesta inoltrata a valle.
 *
 * <p>Riscrivere l'header sulla richiesta serve al gateway: Spring Cloud Gateway MVC
 * inoltra gli header in ingresso, quindi un id generato qui arriva a events-service
 * e stands-service senza dover toccare le route.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String MDC_HTTP_METHOD = "httpMethod";
    static final String MDC_HTTP_PATH = "httpPath";

    /**
     * L'header arriva da un client non fidato e finisce nei log e in un header di
     * risposta: senza validazione sarebbe log injection (CR/LF nel file di log,
     * dove una riga = un evento) e response splitting. Fuori formato, si rigenera.
     */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String headerName;
    private final String mdcKey;

    public CorrelationIdFilter(String headerName, String mdcKey) {
        this.headerName = headerName;
        this.mdcKey = mdcKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(headerName));
        response.setHeader(headerName, correlationId);
        try (MDC.MDCCloseable correlation = MDC.putCloseable(mdcKey, correlationId);
             MDC.MDCCloseable method = MDC.putCloseable(MDC_HTTP_METHOD, request.getMethod());
             MDC.MDCCloseable path = MDC.putCloseable(MDC_HTTP_PATH, request.getRequestURI())) {
            chain.doFilter(new CorrelationIdRequest(request, headerName, correlationId), response);
        }
    }

    private String resolve(String incoming) {
        if (incoming != null && SAFE_CORRELATION_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /** Richiesta con il correlation id garantito fra gli header, qualunque cosa sia arrivata. */
    private static final class CorrelationIdRequest extends HttpServletRequestWrapper {

        private final String headerName;
        private final String correlationId;

        private CorrelationIdRequest(HttpServletRequest request, String headerName, String correlationId) {
            super(request);
            this.headerName = headerName;
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return headerName.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return headerName.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(correlationId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            // Case-insensitive: se il client ha mandato `x-correlation-id`, non lo duplichiamo.
            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            names.addAll(Collections.list(super.getHeaderNames()));
            names.add(headerName);
            return Collections.enumeration(names);
        }
    }
}
