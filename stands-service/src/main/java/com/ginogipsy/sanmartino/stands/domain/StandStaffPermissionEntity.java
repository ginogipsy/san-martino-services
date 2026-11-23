package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_staff_permissions")
public class StandStaffPermissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "username", nullable = false, length = 120)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 32)
    private StandStaffRole permission;

    @Column(name = "granted_by", length = 120)
    private String grantedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StandStaffPermissionEntity() {
    }

    public StandStaffPermissionEntity(
            UUID id,
            UUID standId,
            String username,
            StandStaffRole permission,
            String grantedBy,
            Instant createdAt
    ) {
        this.id = id;
        this.standId = standId;
        this.username = username;
        this.permission = permission;
        this.grantedBy = grantedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStandId() {
        return standId;
    }

    public void setStandId(UUID standId) {
        this.standId = standId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public StandStaffRole getPermission() {
        return permission;
    }

    public void setPermission(StandStaffRole permission) {
        this.permission = permission;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
