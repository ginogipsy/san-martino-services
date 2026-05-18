package com.ginogipsy.sanmartino.saga;

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

    @Bean
    public RestClient eventsRestClient(@Value("${clients.events-base-url:http://localhost:8081}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public RestClient standsRestClient(@Value("${clients.stands-base-url:http://localhost:8082}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
