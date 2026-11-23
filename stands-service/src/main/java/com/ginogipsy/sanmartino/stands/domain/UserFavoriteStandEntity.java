package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_favorite_stands")
public class UserFavoriteStandEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(name = "username", nullable = false, length = 120)
    private String username;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserFavoriteStandEntity() {
    }

    public UserFavoriteStandEntity(UUID id, String userId, String username, UUID standId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.standId = standId;
        this.createdAt = createdAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getStandId() {
        return standId;
    }

    public void setStandId(UUID standId) {
        this.standId = standId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
