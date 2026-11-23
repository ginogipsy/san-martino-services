package com.ginogipsy.sanmartino.stands.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "stand_order_items")
public class StandOrderItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private StandOrderEntity order;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Column(name = "menu_item_name", nullable = false, length = 200)
    private String menuItemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private MenuKind kind;

    @Column(name = "ordered_quantity", nullable = false)
    private int orderedQuantity;

    @Column(name = "cooked_quantity", nullable = false)
    private int cookedQuantity;

    @Column(name = "delivered_quantity", nullable = false)
    private int deliveredQuantity;

    protected StandOrderItemEntity() {
    }

    public StandOrderItemEntity(
            UUID id,
            int slotNumber,
            UUID menuItemId,
            String menuItemName,
            MenuKind kind,
            int orderedQuantity,
            int cookedQuantity,
            int deliveredQuantity
    ) {
        this.id = id;
        this.slotNumber = slotNumber;
        this.menuItemId = menuItemId;
        this.menuItemName = menuItemName;
        this.kind = kind;
        this.orderedQuantity = orderedQuantity;
        this.cookedQuantity = cookedQuantity;
        this.deliveredQuantity = deliveredQuantity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StandOrderEntity getOrder() {
        return order;
    }

    public void setOrder(StandOrderEntity order) {
        this.order = order;
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

    public String getMenuItemName() {
        return menuItemName;
    }

    public void setMenuItemName(String menuItemName) {
        this.menuItemName = menuItemName;
    }

    public MenuKind getKind() {
        return kind;
    }

    public void setKind(MenuKind kind) {
        this.kind = kind;
    }

    public int getOrderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(int orderedQuantity) {
        this.orderedQuantity = orderedQuantity;
    }

    public int getCookedQuantity() {
        return cookedQuantity;
    }

    public void setCookedQuantity(int cookedQuantity) {
        this.cookedQuantity = cookedQuantity;
    }

    public int getDeliveredQuantity() {
        return deliveredQuantity;
    }

    public void setDeliveredQuantity(int deliveredQuantity) {
        this.deliveredQuantity = deliveredQuantity;
    }
}
