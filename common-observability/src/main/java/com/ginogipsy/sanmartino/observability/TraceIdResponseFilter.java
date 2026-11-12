package com.ginogipsy.sanmartino.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Riemette il {@code traceId} corrente (W3C Trace Context / OpenTelemetry) nell'header di
 * risposta HTTP (default: {@code X-Trace-Id}), facilitando la ricerca immediata della traccia
 * in Grafana Tempo a partire da una chiamata client o curl.
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private final Tracer tracer;
    private final String headerName;

    public TraceIdResponseFilter(Tracer tracer, String headerName) {
        this.tracer = tracer;
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (headerName != null && !headerName.isBlank()) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                String traceId = currentSpan.context().traceId();
                if (traceId != null && !traceId.isBlank() && !response.containsHeader(headerName)) {
                    response.setHeader(headerName, traceId);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
