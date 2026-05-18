package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
public class MenuItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stand_id", nullable = false)
    private StandEntity stand;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description_it", nullable = false, columnDefinition = "text")
    private String descriptionIt;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "available_plates", nullable = false)
    private Integer availablePlates;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private MenuKind kind;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "menu_item_keywords",
            joinColumns = @JoinColumn(name = "menu_item_id")
    )
    @Column(name = "keyword", nullable = false, length = 100)
    private List<String> keywords = new ArrayList<>();

    protected MenuItemEntity() {
        // JPA
    }

    public MenuItemEntity(
            UUID id,
            String name,
            String descriptionIt,
            String descriptionEn,
            Integer availablePlates,
            MenuKind kind,
            List<String> keywords
    ) {
        this.id = id;
        this.name = name;
        this.descriptionIt = descriptionIt;
        this.descriptionEn = descriptionEn;
        this.availablePlates = availablePlates;
        this.kind = kind;
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public StandEntity getStand() { return stand; }
    public void setStand(StandEntity stand) { this.stand = stand; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescriptionIt() { return descriptionIt; }
    public void setDescriptionIt(String descriptionIt) { this.descriptionIt = descriptionIt; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public Integer getAvailablePlates() { return availablePlates; }
    public void setAvailablePlates(Integer availablePlates) { this.availablePlates = availablePlates; }

    public MenuKind getKind() { return kind; }
    public void setKind(MenuKind kind) { this.kind = kind; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
    }
}
