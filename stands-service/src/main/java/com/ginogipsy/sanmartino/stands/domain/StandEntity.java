package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stands")
@EntityListeners(AuditingEntityListener.class)
public class StandEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description_it", nullable = false, columnDefinition = "text")
    private String descriptionIt;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "first_participation_year", nullable = false)
    private Integer firstParticipationYear;

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

    @OneToMany(mappedBy = "stand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StandOwnerEntity> owners = new ArrayList<>();

    @OneToMany(mappedBy = "stand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MenuItemEntity> menuItems = new ArrayList<>();

    protected StandEntity() {
        // JPA
    }

    public StandEntity(
            UUID id,
            Integer number,
            String name,
            String descriptionIt,
            String descriptionEn,
            Integer firstParticipationYear,
            Double latitude,
            Double longitude
    ) {
        this.id = id;
        this.number = number;
        this.name = name;
        this.descriptionIt = descriptionIt;
        this.descriptionEn = descriptionEn;
        this.firstParticipationYear = firstParticipationYear;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void addOwner(StandOwnerEntity owner) {
        owners.add(owner);
        owner.setStand(this);
    }

    public void removeOwner(StandOwnerEntity owner) {
        owners.remove(owner);
        owner.setStand(null);
    }

    public void addMenuItem(MenuItemEntity item) {
        menuItems.add(item);
        item.setStand(this);
    }

    public void removeMenuItem(MenuItemEntity item) {
        menuItems.remove(item);
        item.setStand(null);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescriptionIt() { return descriptionIt; }
    public void setDescriptionIt(String descriptionIt) { this.descriptionIt = descriptionIt; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public Integer getFirstParticipationYear() { return firstParticipationYear; }
    public void setFirstParticipationYear(Integer firstParticipationYear) { this.firstParticipationYear = firstParticipationYear; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<StandOwnerEntity> getOwners() { return owners; }
    public List<MenuItemEntity> getMenuItems() { return menuItems; }
}
