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
     * L'interceptor del correlation id (bean di common-observability) va aggiunto a mano:
     * questi client non partono dal {@code RestClient.Builder} auto-configurato, quindi
     * i {@code RestClientCustomizer} non li toccherebbero. Senza di lui la saga
     * risulterebbe scollegata dalle chiamate a events-service e stands-service in Loki.
     */
    @Bean
    public RestClient eventsRestClient(@Value("${clients.events-base-url:http://localhost:8081}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId)
                .build();
    }

    @Bean
    public RestClient standsRestClient(@Value("${clients.stands-base-url:http://localhost:8082}") String baseUrl,
                                       CorrelationIdPropagationInterceptor correlationId) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(correlationId)
                .build();
    }
}
