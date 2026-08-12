package com.ginogipsy.sanmartino.observability;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propaga il correlation id sulle chiamate HTTP in uscita, così la stessa richiesta
 * resta seguibile in Loki attraverso più servizi (es. saga-orchestrator che chiama
 * events-service e stands-service).
 *
 * <p>Va agganciato al {@code RestClient}: {@code RestClient.builder().requestInterceptor(bean)}.
 * Il bean è esposto da {@link ObservabilityAutoConfiguration}.
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final String headerName;
    private final String mdcKey;

    public CorrelationIdPropagationInterceptor(String headerName, String mdcKey) {
        this.headerName = headerName;
        this.mdcKey = mdcKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(mdcKey);
        if (correlationId != null && !request.getHeaders().containsHeader(headerName)) {
            request.getHeaders().add(headerName, correlationId);
        }
        return execution.execute(request, body);
    }
}
