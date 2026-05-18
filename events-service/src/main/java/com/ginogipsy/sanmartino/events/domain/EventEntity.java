package com.ginogipsy.sanmartino.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener.class)
public class EventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "place", nullable = false, length = 200)
    private String place;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description_it", nullable = false, columnDefinition = "text")
    private String descriptionIt;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventEntity() {
        // JPA
    }

    public EventEntity(
            UUID id,
            String name,
            String place,
            LocalDate startDate,
            LocalDate endDate,
            String descriptionIt,
            String descriptionEn
    ) {
        this.id = id;
        this.name = name;
        this.place = place;
        this.startDate = startDate;
        this.endDate = endDate;
        this.descriptionIt = descriptionIt;
        this.descriptionEn = descriptionEn;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getDescriptionIt() { return descriptionIt; }
    public void setDescriptionIt(String descriptionIt) { this.descriptionIt = descriptionIt; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
