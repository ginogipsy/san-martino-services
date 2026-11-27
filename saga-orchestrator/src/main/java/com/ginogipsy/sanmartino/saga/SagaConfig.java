package com.ginogipsy.sanmartino.saga;

import com.ginogipsy.sanmartino.observability.CorrelationIdPropagationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
public class SagaConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Aggancia sia {@link ObservationRegistry} (per la creazione degli span client e la
     * propagazione automatica dell'header W3C {@code traceparent} verso events-service e
     * stands-service) sia {@link CorrelationIdPropagationInterceptor} (per la propagazione
     * di {@code X-Correlation-Id}).
     */
    @Bean
    public RestClient eventsRestClient(@Value("${clients.events-base-url:http://localhost:8081}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId,
                                       ObjectProvider<ObservationRegistry> observationRegistry) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId);
        observationRegistry.ifAvailable(builder::observationRegistry);
        return builder.build();
    }

    @Bean
    public RestClient standsRestClient(@Value("${clients.stands-base-url:http://localhost:8082}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId,
                                       ObjectProvider<ObservationRegistry> observationRegistry) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId);
        observationRegistry.ifAvailable(builder::observationRegistry);
        return builder.build();
    }
}
