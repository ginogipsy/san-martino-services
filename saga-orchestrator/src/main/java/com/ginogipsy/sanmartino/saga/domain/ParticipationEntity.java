package com.ginogipsy.sanmartino.saga.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "participations")
public class ParticipationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaInstanceEntity saga;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    protected ParticipationEntity() {
        // JPA
    }

    public ParticipationEntity(UUID id, UUID eventId, UUID standId) {
        this.id = id;
        this.eventId = eventId;
        this.standId = standId;
    }

    public UUID getId() { return id; }

    public SagaInstanceEntity getSaga() { return saga; }
    public void setSaga(SagaInstanceEntity saga) { this.saga = saga; }

    public UUID getEventId() { return eventId; }
    public UUID getStandId() { return standId; }
}
