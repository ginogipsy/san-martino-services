package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_event_participations")
@EntityListeners(AuditingEntityListener.class)
public class StandEventParticipationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stand_id", nullable = false)
    private StandEntity stand;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "description_it", nullable = false, columnDefinition = "text")
    private String descriptionIt;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StandEventParticipationEntity() {
        // JPA
    }

    public StandEventParticipationEntity(
            UUID id,
            UUID eventId,
            Integer number,
            String descriptionIt,
            String descriptionEn,
            Double latitude,
            Double longitude
    ) {
        this.id = id;
        this.eventId = eventId;
        this.number = number;
        this.descriptionIt = descriptionIt;
        this.descriptionEn = descriptionEn;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StandEntity getStand() {
        return stand;
    }

    public void setStand(StandEntity stand) {
        this.stand = stand;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getDescriptionIt() {
        return descriptionIt;
    }

    public void setDescriptionIt(String descriptionIt) {
        this.descriptionIt = descriptionIt;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
