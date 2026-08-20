package com.ginogipsy.sanmartino.notifications.config;

import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationRecordInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Questa factory ha il nome che fa arretrare l'auto-configurazione di Boot, che
 * altrimenti aggancerebbe da sé il {@code RecordInterceptor} di common-observability:
 * il test presidia l'aggancio manuale, senza cui il correlation id non arriverebbe
 * nell'MDC del listener e la traccia si fermerebbe a Kafka.
 */
class KafkaConsumerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.consumer.group-id=notifications-service");

    @Test
    void attachesTheCorrelationRecordInterceptorToTheListenerFactory() {
        runner.withUserConfiguration(ObservabilityStub.class).run(context -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);

            assertThat(factory.getRecordInterceptor())
                    .isSameAs(context.getBean(KafkaCorrelationRecordInterceptor.class));
        });
    }

    /** Con {@code sanmartino.observability.enabled=false} l'interceptor non esiste: il servizio parte comunque. */
    @Test
    void startsWithoutTheInterceptorWhenObservabilityIsDisabled() {
        runner.run(context -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);

            assertThat(factory.getRecordInterceptor()).isNull();
        });
    }

    /**
     * Dichiara il bean con gli stessi parametri generici dell'auto-configurazione:
     * è quella firma che il punto di iniezione deve saper risolvere.
     */
    @Configuration
    static class ObservabilityStub {

        @Bean
        KafkaCorrelationRecordInterceptor<Object, Object> kafkaCorrelationRecordInterceptor() {
            return new KafkaCorrelationRecordInterceptor<>("X-Correlation-Id", "correlationId");
        }
    }
}
