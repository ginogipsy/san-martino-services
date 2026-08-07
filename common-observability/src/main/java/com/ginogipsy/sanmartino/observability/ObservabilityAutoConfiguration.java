package com.ginogipsy.sanmartino.observability;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.bind.annotation.RestController;

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
}
