package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_search_logs")
public class StandSearchLogEntity {

    @Id
    private UUID id;

    @Column(name = "raw_query", nullable = false, length = 200)
    private String rawQuery;

    @Column(name = "normalized_query", nullable = false, length = 200)
    private String normalizedQuery;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind_filter", length = 10)
    private MenuKind kindFilter;

    @Column(name = "only_available", nullable = false)
    private boolean onlyAvailable;

    @Column(name = "results_count", nullable = false)
    private int resultsCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StandSearchLogEntity() {
    }

    public StandSearchLogEntity(
            UUID id,
            String rawQuery,
            String normalizedQuery,
            MenuKind kindFilter,
            boolean onlyAvailable,
            int resultsCount,
            Instant createdAt
    ) {
        this.id = id;
        this.rawQuery = rawQuery;
        this.normalizedQuery = normalizedQuery;
        this.kindFilter = kindFilter;
        this.onlyAvailable = onlyAvailable;
        this.resultsCount = resultsCount;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public MenuKind getKindFilter() {
        return kindFilter;
    }

    public void setKindFilter(MenuKind kindFilter) {
        this.kindFilter = kindFilter;
    }

    public boolean isOnlyAvailable() {
        return onlyAvailable;
    }

    public void setOnlyAvailable(boolean onlyAvailable) {
        this.onlyAvailable = onlyAvailable;
    }

    public int getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(int resultsCount) {
        this.resultsCount = resultsCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
