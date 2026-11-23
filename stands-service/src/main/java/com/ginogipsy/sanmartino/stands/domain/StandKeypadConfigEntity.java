package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_keypad_configs")
public class StandKeypadConfigEntity {

    @Id
    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "device_token", nullable = false, length = 120)
    private String deviceToken;

    @Column(name = "next_order_number", nullable = false)
    private int nextOrderNumber;

    @Column(name = "last_cashier_seen_at")
    private Instant lastCashierSeenAt;

    @Column(name = "last_kitchen_seen_at")
    private Instant lastKitchenSeenAt;

    @Column(name = "last_delivery_seen_at")
    private Instant lastDeliverySeenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StandKeypadConfigEntity() {
    }

    public StandKeypadConfigEntity(
            UUID standId,
            String deviceToken,
            int nextOrderNumber,
            Instant updatedAt
    ) {
        this.standId = standId;
        this.deviceToken = deviceToken;
        this.nextOrderNumber = nextOrderNumber;
        this.updatedAt = updatedAt;
    }

    public UUID getStandId() {
        return standId;
    }

    public void setStandId(UUID standId) {
        this.standId = standId;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public int getNextOrderNumber() {
        return nextOrderNumber;
    }

    public void setNextOrderNumber(int nextOrderNumber) {
        this.nextOrderNumber = nextOrderNumber;
    }

    public Instant getLastCashierSeenAt() {
        return lastCashierSeenAt;
    }

    public void setLastCashierSeenAt(Instant lastCashierSeenAt) {
        this.lastCashierSeenAt = lastCashierSeenAt;
    }

    public Instant getLastKitchenSeenAt() {
        return lastKitchenSeenAt;
    }

    public void setLastKitchenSeenAt(Instant lastKitchenSeenAt) {
        this.lastKitchenSeenAt = lastKitchenSeenAt;
    }

    public Instant getLastDeliverySeenAt() {
        return lastDeliverySeenAt;
    }

    public void setLastDeliverySeenAt(Instant lastDeliverySeenAt) {
        this.lastDeliverySeenAt = lastDeliverySeenAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
