package com.ginogipsy.sanmartino.saga.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
public class SagaInstanceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SagaStatus status;

    @Column(name = "created_event_id")
    private UUID createdEventId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<SagaStepEntity> steps = new ArrayList<>();

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ParticipationEntity> participations = new ArrayList<>();

    protected SagaInstanceEntity() {
        // JPA
    }

    public SagaInstanceEntity(UUID id, String type, SagaStatus status, Instant startedAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.startedAt = startedAt;
    }

    public void addStep(SagaStepEntity step) {
        steps.add(step);
        step.setSaga(this);
    }

    public void addParticipation(ParticipationEntity participation) {
        participations.add(participation);
        participation.setSaga(this);
    }

    public UUID getId() { return id; }
    public String getType() { return type; }

    public SagaStatus getStatus() { return status; }
    public void setStatus(SagaStatus status) { this.status = status; }

    public UUID getCreatedEventId() { return createdEventId; }
    public void setCreatedEventId(UUID createdEventId) { this.createdEventId = createdEventId; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<SagaStepEntity> getSteps() { return steps; }
    public List<ParticipationEntity> getParticipations() { return participations; }
}
