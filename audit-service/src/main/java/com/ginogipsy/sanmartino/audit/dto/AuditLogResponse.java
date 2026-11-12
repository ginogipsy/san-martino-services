package com.ginogipsy.sanmartino.audit.dto;

import com.ginogipsy.sanmartino.audit.entity.AuditLogEntity;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        String id,
        String serviceId,
        String action,
        String userId,
        String username,
        String resourceId,
        String description,
        String correlationId,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static AuditLogResponse fromEntity(AuditLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AuditLogResponse(
                entity.getId(),
                entity.getServiceId(),
                entity.getAction(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getResourceId(),
                entity.getDescription(),
                entity.getCorrelationId(),
                entity.getTimestamp(),
                entity.getMetadata()
        );
    }
}
