package com.ginogipsy.sanmartino.saga;

import com.ginogipsy.sanmartino.observability.CorrelationIdPropagationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableJpaAuditing
public class SagaConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Parte dal {@code RestClient.Builder} auto-configurato da Spring Boot (clonato per
     * isolare baseUrl e interceptor fra i due client): in questo modo {@code ObservationRestClientCustomizer}
     * di Micrometer Tracing resta agganciato e propaga automaticamente l'header W3C
     * {@code traceparent} verso events-service e stands-service, mentre {@link CorrelationIdPropagationInterceptor}
     * propaga {@code X-Correlation-Id}.
     */
    @Bean
    public RestClient eventsRestClient(RestClient.Builder builder,
                                       @Value("${clients.events-base-url:http://localhost:8081}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId)
                .build();
    }

    @Bean
    public RestClient standsRestClient(RestClient.Builder builder,
                                       @Value("${clients.stands-base-url:http://localhost:8082}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId)
                .build();
    }
}
