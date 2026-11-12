package com.ginogipsy.sanmartino.observability.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAspectTest {

    private List<AuditEvent> publishedEvents;
    private AuditPublisher auditPublisher;
    private AuditUserResolver auditUserResolver;
    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        auditPublisher = publishedEvents::add;
        auditUserResolver = () -> new AuditUserResolver.AuditUser("user-123", "mario.rossi");
        aspect = new AuditAspect("stands-service", "correlationId", auditPublisher, auditUserResolver);
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    static class SampleAuditedService {

        @Auditable(
                action = "STAND:PRICE_UPDATE",
                description = "'Aggiornato prezzo dello stand ' + #standId + ' a ' + #newPrice",
                resourceId = "#standId"
        )
        public String updatePrice(String standId, double newPrice) {
            return "OK";
        }

        @Auditable(
                action = "EVENT:CREATE",
                description = "'Creato evento: ' + #result",
                resourceId = "#result"
        )
        public String createEvent(String eventName) {
            return "event-" + eventName;
        }

        @Auditable(action = "FAIL:ACTION")
        public void failingMethod() {
            throw new IllegalStateException("Business validation failed");
        }
    }

    @Test
    void publishesAuditEventWithEvaluatedSpelAndContext() {
        SampleAuditedService service = proxy(new SampleAuditedService());

        String result = service.updatePrice("stand-42", 5.50);

        assertThat(result).isEqualTo("OK");
        assertThat(publishedEvents).hasSize(1);

        AuditEvent event = publishedEvents.get(0);
        assertThat(event.id()).isNotBlank();
        assertThat(event.serviceId()).isEqualTo("stands-service");
        assertThat(event.action()).isEqualTo("STAND:PRICE_UPDATE");
        assertThat(event.userId()).isEqualTo("user-123");
        assertThat(event.username()).isEqualTo("mario.rossi");
        assertThat(event.resourceId()).isEqualTo("stand-42");
        assertThat(event.description()).isEqualTo("Aggiornato prezzo dello stand stand-42 a 5.5");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.metadata()).containsKey("method");
    }

    @Test
    void supportsResultVariableInSpel() {
        SampleAuditedService service = proxy(new SampleAuditedService());

        String result = service.createEvent("festa-del-vino");

        assertThat(result).isEqualTo("event-festa-del-vino");
        assertThat(publishedEvents).hasSize(1);

        AuditEvent event = publishedEvents.get(0);
        assertThat(event.action()).isEqualTo("EVENT:CREATE");
        assertThat(event.resourceId()).isEqualTo("event-festa-del-vino");
        assertThat(event.description()).isEqualTo("Creato evento: event-festa-del-vino");
    }

    @Test
    void doesNotPublishAuditEventOnException() {
        SampleAuditedService service = proxy(new SampleAuditedService());

        assertThatThrownBy(service::failingMethod)
                .isInstanceOf(IllegalStateException.class);

        assertThat(publishedEvents).isEmpty();
    }
}
