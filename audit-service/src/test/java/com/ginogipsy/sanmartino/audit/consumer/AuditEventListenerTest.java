package com.ginogipsy.sanmartino.audit.consumer;

import com.ginogipsy.sanmartino.audit.entity.AuditLogEntity;
import com.ginogipsy.sanmartino.audit.repository.AuditLogRepository;
import com.ginogipsy.sanmartino.observability.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditEventListener listener;

    @Test
    void savesAuditEventToDatabase() {
        AuditEvent event = new AuditEvent(
                "evt-100",
                "stands-service",
                "STAND:CREATE",
                "user-99",
                "admin",
                "stand-5",
                "Creato nuovo stand",
                "corr-abc",
                Instant.parse("2026-11-11T15:00:00Z"),
                Map.of("category", "FOOD")
        );

        listener.onAuditEvent(event);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("evt-100");
        assertThat(saved.getServiceId()).isEqualTo("stands-service");
        assertThat(saved.getAction()).isEqualTo("STAND:CREATE");
        assertThat(saved.getUserId()).isEqualTo("user-99");
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getResourceId()).isEqualTo("stand-5");
        assertThat(saved.getDescription()).isEqualTo("Creato nuovo stand");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(saved.getMetadata()).containsEntry("category", "FOOD");
    }

    @Test
    void ignoresNullEvent() {
        listener.onAuditEvent(null);

        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
