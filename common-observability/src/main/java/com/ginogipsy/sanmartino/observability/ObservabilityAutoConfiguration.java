package com.ginogipsy.sanmartino.observability;

import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationInterceptor;
import com.ginogipsy.sanmartino.observability.kafka.KafkaCorrelationRecordInterceptor;
import jakarta.servlet.Filter;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registra l'osservabilità applicativa nei servizi che hanno questa libreria sul
 * classpath: nessuna configurazione richiesta lato servizio.
 *
 * <p>{@code @EnableAspectJAutoProxy} non serve: {@code spring-boot-starter-aspectj}
 * porta aspectjweaver, che è la condizione con cui {@code AopAutoConfiguration}
 * attiva i proxy (CGLIB per default, necessari perché i controller implementano
 * le interfacce generate da openapi-generator e un proxy JDK perderebbe le
 * annotazioni MVC della classe).
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "sanmartino.observability", name = "enabled", matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    /**
     * L'espressione del pointcut nomina {@code @RestController}: senza spring-web sul
     * classpath AspectJ non riuscirebbe a risolvere quel tipo. Tutti i servizi del
     * monorepo lo hanno; la condizione tiene comunque la libreria innocua altrove.
     */
    @Bean
    @ConditionalOnClass(RestController.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sanmartino.observability.logging", name = "enabled", matchIfMissing = true)
    public MethodLoggingAspect methodLoggingAspect(ObservabilityProperties properties) {
        return new MethodLoggingAspect(properties.logging());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    @ConditionalOnProperty(prefix = "sanmartino.observability.correlation", name = "enabled", matchIfMissing = true)
    public FilterRegistrationBean<Filter> correlationIdFilterRegistration(ObservabilityProperties properties) {
        ObservabilityProperties.Correlation correlation = properties.correlation();
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(correlation.headerName(), correlation.mdcKey()));
        // Primo della catena: tutto ciò che viene loggato dopo, security compresa,
        // deve già avere il correlation id in MDC.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdFilter");
        return registration;
    }

    @Bean
    @ConditionalOnClass(ClientHttpRequestInterceptor.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sanmartino.observability.correlation", name = "enabled", matchIfMissing = true)
    public CorrelationIdPropagationInterceptor correlationIdPropagationInterceptor(ObservabilityProperties properties) {
        ObservabilityProperties.Correlation correlation = properties.correlation();
        return new CorrelationIdPropagationInterceptor(correlation.headerName(), correlation.mdcKey());
    }

    /**
     * Configura la propagazione automatica del correlation id su Kafka, se presente sul classpath.
     *
     * <p>Le due direzioni usano meccanismi diversi: in consumo Spring Kafka accetta un
     * bean {@code RecordInterceptor}, in produzione l'interceptor è un'interfaccia di
     * Apache Kafka che vive nella config map del producer.
     */
    @Configuration
    @ConditionalOnClass({DefaultKafkaProducerFactory.class, ConcurrentKafkaListenerContainerFactory.class})
    @ConditionalOnProperty(prefix = "sanmartino.observability.correlation", name = "enabled", matchIfMissing = true)
    public static class KafkaObservabilityConfiguration {

        /**
         * Il bean da solo non basta: lo applica alla listener container factory
         * l'auto-configurazione di Boot, quindi un servizio che dichiara una factory
         * propria deve chiamare {@code setRecordInterceptor} a mano.
         */
        @Bean
        @ConditionalOnMissingBean
        public KafkaCorrelationRecordInterceptor<Object, Object> kafkaCorrelationRecordInterceptor(
                ObservabilityProperties properties) {
            ObservabilityProperties.Correlation correlation = properties.correlation();
            return new KafkaCorrelationRecordInterceptor<>(correlation.headerName(), correlation.mdcKey());
        }

        /**
         * Aggiunge {@link KafkaCorrelationInterceptor} agli {@code interceptor.classes}
         * del producer auto-configurato, insieme alle due chiavi che il suo
         * {@code configure()} legge: Kafka lo istanzia per nome, quindi l'header name e
         * la chiave MDC possono arrivargli solo dalla config map.
         *
         * <p>Le due chiavi custom non producono il warning "supplied but isn't a known
         * config": Kafka passa a {@code configure()} una {@code RecordingMap}, che segna
         * come usata ogni chiave letta con {@code get()}.
         */
        @Bean
        @ConditionalOnClass(DefaultKafkaProducerFactoryCustomizer.class)
        @ConditionalOnMissingBean(name = "kafkaCorrelationProducerFactoryCustomizer")
        public DefaultKafkaProducerFactoryCustomizer kafkaCorrelationProducerFactoryCustomizer(
                ObservabilityProperties properties) {
            ObservabilityProperties.Correlation correlation = properties.correlation();
            return producerFactory -> producerFactory.updateConfigs(Map.of(
                    ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                    withCorrelationInterceptor(producerFactory.getConfigurationProperties()),
                    KafkaCorrelationInterceptor.HEADER_NAME_CONFIG, correlation.headerName(),
                    KafkaCorrelationInterceptor.MDC_KEY_CONFIG, correlation.mdcKey()));
        }

        /**
         * {@code updateConfigs} sovrascrive la chiave, non la accoda: senza questo merge
         * un {@code interceptor.classes} configurato dal servizio verrebbe perso.
         */
        private static String withCorrelationInterceptor(Map<String, Object> producerConfigs) {
            String correlationInterceptor = KafkaCorrelationInterceptor.class.getName();
            String configured = asCsv(producerConfigs.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG));
            if (configured.isBlank() || configured.contains(correlationInterceptor)) {
                return configured.isBlank() ? correlationInterceptor : configured;
            }
            return configured + "," + correlationInterceptor;
        }

        /** {@code interceptor.classes} ammette CSV, {@code List<String>} e {@code List<Class>}. */
        private static String asCsv(Object configured) {
            return switch (configured) {
                case null -> "";
                case Collection<?> classes -> classes.stream()
                        .map(KafkaObservabilityConfiguration::className)
                        .collect(Collectors.joining(","));
                default -> configured.toString();
            };
        }

        private static String className(Object value) {
            return value instanceof Class<?> type ? type.getName() : String.valueOf(value);
        }
    }
}
