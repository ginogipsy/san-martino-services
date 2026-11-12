package com.ginogipsy.sanmartino.audit.controller;

import com.ginogipsy.sanmartino.audit.configuration.AuditSecurityConfig;
import com.ginogipsy.sanmartino.audit.entity.AuditLogEntity;
import com.ginogipsy.sanmartino.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import(AuditSecurityConfig.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void getAuditLogsWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/v1/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAuditLogsWithNonAdminRoleReturns403() throws Exception {
        mockMvc.perform(get("/v1/audit-logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAuditLogsWithAdminRoleReturns200AndPage() throws Exception {
        AuditLogEntity entity = new AuditLogEntity(
                "audit-1",
                "stands-service",
                "STAND:PRICE_UPDATE",
                "user-1",
                "mario.rossi",
                "stand-10",
                "Modificato prezzo a 4.50",
                "corr-123",
                Instant.parse("2026-11-11T12:00:00Z"),
                Map.of("method", "updatePrice")
        );

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        mockMvc.perform(get("/v1/audit-logs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("audit-1"))
                .andExpect(jsonPath("$.content[0].serviceId").value("stands-service"))
                .andExpect(jsonPath("$.content[0].action").value("STAND:PRICE_UPDATE"))
                .andExpect(jsonPath("$.content[0].resourceId").value("stand-10"))
                .andExpect(jsonPath("$.content[0].description").value("Modificato prezzo a 4.50"));
    }

    @Test
    void getAuditLogByIdReturns200WhenFound() throws Exception {
        AuditLogEntity entity = new AuditLogEntity(
                "audit-1",
                "stands-service",
                "STAND:PRICE_UPDATE",
                "user-1",
                "mario.rossi",
                "stand-10",
                "Modificato prezzo a 4.50",
                "corr-123",
                Instant.parse("2026-11-11T12:00:00Z"),
                Map.of()
        );

        when(auditLogRepository.findById("audit-1")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/v1/audit-logs/audit-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("audit-1"))
                .andExpect(jsonPath("$.action").value("STAND:PRICE_UPDATE"));
    }

    @Test
    void getAuditLogByIdReturns404WhenNotFound() throws Exception {
        when(auditLogRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/audit-logs/missing")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
