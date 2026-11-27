package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stand_keypad_slots")
public class StandKeypadSlotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "stand_id", nullable = false)
    private UUID standId;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StandKeypadSlotEntity() {
    }

    public StandKeypadSlotEntity(
            UUID id,
            UUID standId,
            int slotNumber,
            UUID menuItemId,
            Instant updatedAt
    ) {
        this.id = id;
        this.standId = standId;
        this.slotNumber = slotNumber;
        this.menuItemId = menuItemId;
        this.updatedAt = updatedAt;
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

    public int getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(UUID menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
