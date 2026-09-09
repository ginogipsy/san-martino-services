package com.ginogipsy.sanmartino.notifications.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ginogipsy.sanmartino.notifications.event.SagaEvent;
import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationRecordInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    @SuppressWarnings({"deprecation", "removal"})
    public ConsumerFactory<String, SagaEvent> sagaEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        JsonDeserializer<SagaEvent> jsonDeserializer =
                new JsonDeserializer<>(SagaEvent.class, objectMapper, false);
        jsonDeserializer.addTrustedPackages("*");

        ErrorHandlingDeserializer<SagaEvent> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SagaEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, SagaEvent> sagaEventConsumerFactory,
            ObjectProvider<KafkaCorrelationRecordInterceptor<Object, Object>> correlationInterceptor) {
        ConcurrentKafkaListenerContainerFactory<String, SagaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaEventConsumerFactory);
        // Il nome di questo bean è quello che Boot usa per la sua factory, quindi la sua
        // auto-configurazione arretra e con lei l'aggancio automatico del RecordInterceptor
        // di common-observability: senza questa riga il correlation id degli header Kafka
        // non arriverebbe mai nell'MDC del listener. ObjectProvider perché l'interceptor
        // non esiste se l'osservabilità è disattivata (sanmartino.observability.enabled).
        correlationInterceptor.ifAvailable(interceptor -> factory.setRecordInterceptor(retyped(interceptor)));
        return factory;
    }

    /**
     * La libreria dichiara l'interceptor {@code <Object, Object>} perché non tocca chiave
     * e valore del record — legge solo gli header e li rimette nell'MDC — quindi usarlo su
     * una factory tipizzata {@code <String, SagaEvent>} è sicuro: il record che restituisce
     * è la stessa istanza che ha ricevuto.
     */
    @SuppressWarnings("unchecked")
    private static RecordInterceptor<String, SagaEvent> retyped(RecordInterceptor<Object, Object> interceptor) {
        return (RecordInterceptor<String, SagaEvent>) (RecordInterceptor<?, ?>) interceptor;
    }
}
