package com.ginogipsy.sanmartino.saga.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_steps")
public class SagaStepEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaInstanceEntity saga;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SagaStepStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    protected SagaStepEntity() {
        // JPA
    }

    public SagaStepEntity(UUID id, String name, int stepOrder, SagaStepStatus status) {
        this.id = id;
        this.name = name;
        this.stepOrder = stepOrder;
        this.status = status;
    }

    public UUID getId() { return id; }

    public SagaInstanceEntity getSaga() { return saga; }
    public void setSaga(SagaInstanceEntity saga) { this.saga = saga; }

    public String getName() { return name; }
    public Integer getStepOrder() { return stepOrder; }

    public SagaStepStatus getStatus() { return status; }
    public void setStatus(SagaStepStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
