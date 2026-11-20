package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_interaction_events")
public class StandInteractionEventEntity {

    @Id
    private UUID id;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private StandInteractionType interactionType;

    @Column(name = "search_query", length = 200)
    private String searchQuery;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StandInteractionEventEntity() {
    }

    public StandInteractionEventEntity(
            UUID id,
            UUID standId,
            UUID menuItemId,
            StandInteractionType interactionType,
            String searchQuery,
            Instant createdAt
    ) {
        this.id = id;
        this.standId = standId;
        this.menuItemId = menuItemId;
        this.interactionType = interactionType;
        this.searchQuery = searchQuery;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
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

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    public StandInteractionType getInteractionType() {
        return interactionType;
    }

    public void setInteractionType(StandInteractionType interactionType) {
        this.interactionType = interactionType;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
