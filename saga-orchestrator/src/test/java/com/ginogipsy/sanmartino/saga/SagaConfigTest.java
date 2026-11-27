package com.ginogipsy.sanmartino.saga;

import com.ginogipsy.sanmartino.observability.CorrelationIdPropagationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SagaConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsEventsAndStandsRestClientsWithObservationRegistry() {
        SagaConfig config = new SagaConfig();
        CorrelationIdPropagationInterceptor correlationId =
                new CorrelationIdPropagationInterceptor("X-Correlation-Id", "correlationId");
        ObservationRegistry registry = ObservationRegistry.create();

        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
        doAnswer(invocation -> {
            Consumer<ObservationRegistry> consumer = invocation.getArgument(0);
            consumer.accept(registry);
            return null;
        }).when(provider).ifAvailable(any());

        RestClient eventsClient = config.eventsRestClient("http://localhost:8081", correlationId, provider);
        RestClient standsClient = config.standsRestClient("http://localhost:8082", correlationId, provider);

        assertThat(eventsClient).isNotNull();
        assertThat(standsClient).isNotNull();
        assertThat(eventsClient).extracting("observationRegistry").isSameAs(registry);
        assertThat(standsClient).extracting("observationRegistry").isSameAs(registry);
    }
}
