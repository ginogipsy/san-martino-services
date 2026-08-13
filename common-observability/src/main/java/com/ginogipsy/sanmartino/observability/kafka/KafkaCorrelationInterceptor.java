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
 */
public class KafkaCorrelationInterceptor<K, V> implements ProducerInterceptor<K, V> {

    private String headerName;
    private String mdcKey;

    @Override
    public void configure(Map<String, ?> configs) {
        this.headerName = configs.get("sanmartino.observability.header") != null 
                ? configs.get("sanmartino.observability.header").toString() 
                : "X-Correlation-Id";
        this.mdcKey = configs.get("sanmartino.observability.mdc-key") != null 
                ? configs.get("sanmartino.observability.mdc-key").toString() 
                : "correlationId";
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
