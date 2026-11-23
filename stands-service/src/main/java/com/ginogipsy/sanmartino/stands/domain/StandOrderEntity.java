package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stand_orders")
public class StandOrderEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "order_number", nullable = false)
    private int orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private StandOrderStatus status;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("slotNumber ASC")
    private List<StandOrderItemEntity> items = new ArrayList<>();

    protected StandOrderEntity() {
    }

    public StandOrderEntity(
            UUID id,
            UUID standId,
            int orderNumber,
            StandOrderStatus status,
            String createdBy,
            Instant createdAt
    ) {
        this.id = id;
        this.standId = standId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void addItem(StandOrderItemEntity item) {
        item.setOrder(this);
        this.items.add(item);
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

    public int getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }

    public StandOrderStatus getStatus() {
        return status;
    }

    public void setStatus(StandOrderStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public List<StandOrderItemEntity> getItems() {
        return items;
    }

    public void setItems(List<StandOrderItemEntity> items) {
        this.items = items;
    }
}
