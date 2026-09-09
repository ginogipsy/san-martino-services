package com.ginogipsy.sanmartino.observability.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Interceptor Spring Kafka che estrae il correlation id dagli header del messaggio
 * in ingresso e lo mette nell'MDC prima di invocare il listener (@KafkaListener).
 */
public class KafkaCorrelationRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    private final String headerName;
    private final String mdcKey;

    public KafkaCorrelationRecordInterceptor(String headerName, String mdcKey) {
        this.headerName = headerName;
        this.mdcKey = mdcKey;
    }

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, org.apache.kafka.clients.consumer.Consumer<K, V> consumer) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null) {
            String correlationId = new String(header.value(), StandardCharsets.UTF_8);
            MDC.put(mdcKey, correlationId);
        }
        return record;
    }

    @Override
    public void success(ConsumerRecord<K, V> record, org.apache.kafka.clients.consumer.Consumer<K, V> consumer) {
        MDC.remove(mdcKey);
    }

    @Override
    public void failure(ConsumerRecord<K, V> record, Exception exception, org.apache.kafka.clients.consumer.Consumer<K, V> consumer) {
        MDC.remove(mdcKey);
    }
}
