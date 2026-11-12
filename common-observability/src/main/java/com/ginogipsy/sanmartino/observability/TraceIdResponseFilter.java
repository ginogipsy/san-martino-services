package com.ginogipsy.sanmartino.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Riemette il {@code traceId} corrente (W3C Trace Context / OpenTelemetry) nell'header di
 * risposta HTTP (default: {@code X-Trace-Id}), facilitando la ricerca immediata della traccia
 * in Grafana Tempo a partire da una chiamata client o curl.
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

    private final Supplier<Tracer> tracerSupplier;
    private final String headerName;

    public TraceIdResponseFilter(Tracer tracer, String headerName) {
        this(() -> tracer, headerName);
    }

    public TraceIdResponseFilter(Supplier<Tracer> tracerSupplier, String headerName) {
        this.tracerSupplier = tracerSupplier;
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId();
        if (traceId != null && !response.containsHeader(headerName)) {
            response.setHeader(headerName, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (traceId != null && !response.isCommitted() && response.getHeaders(headerName).size() > 1) {
                response.setHeader(headerName, traceId);
            }
        }
    }

    private String resolveTraceId() {
        if (headerName == null || headerName.isBlank() || tracerSupplier == null) {
            return null;
        }
        Tracer tracer = tracerSupplier.get();
        if (tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return null;
        }
        String traceId = currentSpan.context().traceId();
        return (traceId != null && !traceId.isBlank()) ? traceId : null;
    }
}

