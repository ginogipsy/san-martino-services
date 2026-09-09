package com.ginogipsy.sanmartino.observability;

import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationInterceptor;
import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationRecordInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica il cablaggio dei due interceptor Kafka, non il loro comportamento: che i
 * bean esistano e che il nome dell'interceptor di produzione finisca davvero negli
 * {@code interceptor.classes} del producer, perché è Kafka a istanziarlo da lì.
 */
class KafkaObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class, ObservabilityAutoConfiguration.class));

    @Test
    void registersTheProducerInterceptorOnTheAutoConfiguredProducerFactory() {
        runner.run(context -> assertThat(interceptorClasses(context))
                .isEqualTo(KafkaCorrelationInterceptor.class.getName()));
    }

    /**
     * {@code updateConfigs} sovrascrive la chiave: se il merge sparisce, un servizio che
     * configura i propri interceptor li perde senza un solo messaggio di errore.
     */
    @Test
    void keepsTheInterceptorsAlreadyConfiguredByTheService() {
        runner.withPropertyValues("spring.kafka.producer.properties.interceptor.classes="
                        + NoopInterceptor.class.getName())
                .run(context -> assertThat(interceptorClasses(context))
                        .isEqualTo(NoopInterceptor.class.getName() + "," + KafkaCorrelationInterceptor.class.getName()));
    }

    @Test
    void doesNotRegisterTheInterceptorTwice() {
        runner.withPropertyValues("spring.kafka.producer.properties.interceptor.classes="
                        + KafkaCorrelationInterceptor.class.getName())
                .run(context -> assertThat(interceptorClasses(context))
                        .isEqualTo(KafkaCorrelationInterceptor.class.getName()));
    }

    /** Header e chiave MDC configurati devono arrivare all'interceptor: passano da qui. */
    @Test
    void passesTheConfiguredHeaderAndMdcKeyToTheProducerConfig() {
        runner.withPropertyValues(
                        "sanmartino.observability.correlation.header-name=X-Traccia",
                        "sanmartino.observability.correlation.mdc-key=tracciaId")
                .run(context -> assertThat(producerConfigs(context))
                        .containsEntry(KafkaCorrelationInterceptor.HEADER_NAME_CONFIG, "X-Traccia")
                        .containsEntry(KafkaCorrelationInterceptor.MDC_KEY_CONFIG, "tracciaId"));
    }

    @Test
    void registersTheConsumerSideRecordInterceptor() {
        runner.run(context -> assertThat(context).hasSingleBean(KafkaCorrelationRecordInterceptor.class));
    }

    @Test
    void backsOffWhenCorrelationIsDisabled() {
        runner.withPropertyValues("sanmartino.observability.correlation.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(KafkaCorrelationRecordInterceptor.class);
                    assertThat(producerConfigs(context)).doesNotContainKey(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
                });
    }

    private static Object interceptorClasses(AssertableApplicationContext context) {
        return producerConfigs(context).get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
    }

    private static Map<String, Object> producerConfigs(AssertableApplicationContext context) {
        return context.getBean(DefaultKafkaProducerFactory.class).getConfigurationProperties();
    }

    /** Sta qui solo per avere un secondo nome di classe da far comparire nel merge. */
    public static class NoopInterceptor implements ProducerInterceptor<Object, Object> {

        @Override
        public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        @Override
        public void configure(Map<String, ?> configs) {
            // No-op
        }
    }
}
