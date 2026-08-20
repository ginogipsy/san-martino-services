package com.ginogipsy.sanmartino.observability.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Interceptor Kafka che estrae il correlation id dall'MDC del thread corrente
 * e lo inietta negli header del messaggio in uscita.
 *
 * <p>Consente di seguire la traccia di una richiesta anche quando attraversa
 * i confini del trasporto asincrono.
 *
 * <p>Non è un bean Spring: {@link ProducerInterceptor} è un'interfaccia di Apache
 * Kafka, che istanzia la classe per nome dalla property {@code interceptor.classes}
 * e la configura passandole la config map del producer. La registrazione automatica
 * la fa {@code ObservabilityAutoConfiguration}, che aggiunge a quella map il nome di
 * questa classe e le due chiavi qui sotto.
 */
public class KafkaCorrelationInterceptor<K, V> implements ProducerInterceptor<K, V> {

    /** Chiave della config map del producer con cui si sovrascrive il nome dell'header. */
    public static final String HEADER_NAME_CONFIG = "sanmartino.observability.header";

    /** Chiave della config map del producer con cui si sovrascrive la chiave MDC. */
    public static final String MDC_KEY_CONFIG = "sanmartino.observability.mdc-key";

    private String headerName;
    private String mdcKey;

    @Override
    public void configure(Map<String, ?> configs) {
        this.headerName = value(configs, HEADER_NAME_CONFIG, "X-Correlation-Id");
        this.mdcKey = value(configs, MDC_KEY_CONFIG, "correlationId");
    }

    private static String value(Map<String, ?> configs, String key, String fallback) {
        Object configured = configs.get(key);
        return configured != null ? configured.toString() : fallback;
    }

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        String correlationId = MDC.get(mdcKey);
        if (correlationId != null) {
            Headers headers = record.headers();
            // Evitiamo duplicati se l'header e' gia' presente
            if (headers.lastHeader(headerName) == null) {
                headers.add(headerName, correlationId.getBytes(StandardCharsets.UTF_8));
            }
        }
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
}
