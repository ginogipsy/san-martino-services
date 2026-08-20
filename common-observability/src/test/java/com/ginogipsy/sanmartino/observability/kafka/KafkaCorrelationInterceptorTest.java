package com.ginogipsy.sanmartino.observability.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaCorrelationInterceptorTest {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final String TOPIC = "sanmartino.saga.events";

    private final KafkaCorrelationInterceptor<String, String> interceptor = configured(HEADER, MDC_KEY);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesTheCorrelationIdFromMdcToTheRecordHeaders() {
        MDC.put(MDC_KEY, "abc-123");

        ProducerRecord<String, String> record = interceptor.onSend(record());

        assertThat(headerOf(record)).isEqualTo("abc-123");
    }

    @Test
    void leavesTheRecordAloneWhenTheMdcIsEmpty() {
        ProducerRecord<String, String> record = interceptor.onSend(record());

        assertThat(record.headers().lastHeader(HEADER)).isNull();
    }

    /** Un evento ripubblicato conserva l'header originale: la traccia non deve spezzarsi. */
    @Test
    void doesNotOverwriteAnHeaderAlreadyPresent() {
        MDC.put(MDC_KEY, "nuovo");
        ProducerRecord<String, String> record = record();
        record.headers().add(HEADER, "originale".getBytes(StandardCharsets.UTF_8));

        assertThat(headerOf(interceptor.onSend(record))).isEqualTo("originale");
    }

    /**
     * Kafka istanzia l'interceptor per nome, quindi header e chiave MDC configurati in
     * {@code sanmartino.observability} possono raggiungerlo solo dalla config map.
     */
    @Test
    void honoursTheConfiguredHeaderNameAndMdcKey() {
        KafkaCorrelationInterceptor<String, String> custom = configured("X-Traccia", "tracciaId");
        MDC.put("tracciaId", "xyz-789");

        ProducerRecord<String, String> record = custom.onSend(record());

        assertThat(record.headers().lastHeader("X-Traccia").value()).asString(StandardCharsets.UTF_8)
                .isEqualTo("xyz-789");
    }

    /** Senza le chiavi custom valgono i default della libreria, non un NPE. */
    @Test
    void fallsBackToTheLibraryDefaults() {
        KafkaCorrelationInterceptor<String, String> bare = new KafkaCorrelationInterceptor<>();
        bare.configure(Map.of());
        MDC.put(MDC_KEY, "def-000");

        assertThat(headerOf(bare.onSend(record()))).isEqualTo("def-000");
    }

    private static KafkaCorrelationInterceptor<String, String> configured(String headerName, String mdcKey) {
        KafkaCorrelationInterceptor<String, String> interceptor = new KafkaCorrelationInterceptor<>();
        interceptor.configure(Map.of(
                KafkaCorrelationInterceptor.HEADER_NAME_CONFIG, headerName,
                KafkaCorrelationInterceptor.MDC_KEY_CONFIG, mdcKey));
        return interceptor;
    }

    private static ProducerRecord<String, String> record() {
        return new ProducerRecord<>(TOPIC, "key", "payload");
    }

    private static String headerOf(ProducerRecord<String, String> record) {
        return new String(record.headers().lastHeader(HEADER).value(), StandardCharsets.UTF_8);
    }
}
