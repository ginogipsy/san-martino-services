package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.KeypadApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.*;
import com.ginogipsy.sanmartino.stands.service.StandKeypadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class KeypadController implements KeypadApi {

    private final StandKeypadService keypadService;
    private final StandMapper mapper;

    public KeypadController(StandKeypadService keypadService, StandMapper mapper) {
        this.keypadService = keypadService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<AccessibleKeypadStand>> listAccessibleKeypadStands() {
        List<AccessibleKeypadStand> stands = keypadService.listAccessibleStands().stream()
                .map(mapper::toAccessibleKeypadStand)
                .toList();
        return ResponseEntity.ok(stands);
    }

    @Override
    public ResponseEntity<StandKeypadState> getStandKeypadState(
            UUID id,
            KeypadStationRole station,
            String xDeviceToken
    ) {
        com.ginogipsy.sanmartino.stands.domain.KeypadStationRole domainStation = station == null
                ? null
                : com.ginogipsy.sanmartino.stands.domain.KeypadStationRole.valueOf(station.getValue());
        StandKeypadService.KeypadStateView view = keypadService.getKeypadState(id, domainStation, xDeviceToken);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> updateStandKeypadSlots(
            UUID id,
            StandKeypadSlotsUpdate body
    ) {
        List<StandKeypadService.SlotAssignmentInput> inputs = body.getSlots() == null
                ? List.of()
                : body.getSlots().stream()
                .map(s -> new StandKeypadService.SlotAssignmentInput(s.getSlotNumber(), s.getMenuItemId()))
                .toList();
        boolean regenerateToken = Boolean.TRUE.equals(body.getRegenerateDeviceToken());
        StandKeypadService.KeypadStateView view = keypadService.updateSlots(id, inputs, regenerateToken);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> addStandStaffPermission(
            UUID id,
            StandStaffPermissionCreate body
    ) {
        com.ginogipsy.sanmartino.stands.domain.StandStaffRole role =
                com.ginogipsy.sanmartino.stands.domain.StandStaffRole.valueOf(body.getPermission().getValue());
        StandKeypadService.KeypadStateView view = keypadService.addStaffPermission(id, body.getUsername(), role);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> deleteStandStaffPermission(
            UUID id,
            UUID permissionId
    ) {
        StandKeypadService.KeypadStateView view = keypadService.deleteStaffPermission(id, permissionId);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> createStandKeypadOrder(
            UUID id,
            StandKeypadOrderCreate body,
            String xDeviceToken
    ) {
        List<StandKeypadService.OrderItemInput> items = body.getItems() == null
                ? List.of()
                : body.getItems().stream()
                .map(it -> new StandKeypadService.OrderItemInput(it.getSlotNumber(), it.getQuantity()))
                .toList();
        StandKeypadService.KeypadStateView view = keypadService.createOrder(id, items, xDeviceToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> pressKitchenKeypadSlot(
            UUID id,
            StandKeypadSlotPress body,
            String xDeviceToken
    ) {
        int qty = body.getQuantity() != null ? body.getQuantity() : 1;
        StandKeypadService.KeypadStateView view = keypadService.pressKitchenSlot(
                id,
                body.getSlotNumber(),
                qty,
                xDeviceToken
        );
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> pressDeliveryKeypadSlot(
            UUID id,
            StandKeypadSlotPress body,
            String xDeviceToken
    ) {
        int qty = body.getQuantity() != null ? body.getQuantity() : 1;
        StandKeypadService.KeypadStateView view = keypadService.pressDeliverySlot(
                id,
                body.getSlotNumber(),
                qty,
                xDeviceToken
        );
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> resetStandKeypadCounter(UUID id) {
        StandKeypadService.KeypadStateView view = keypadService.resetOrderCounter(id);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> cancelStandKeypadOrder(UUID id, UUID orderId) {
        StandKeypadService.KeypadStateView view = keypadService.cancelOrder(id, orderId);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }

    @Override
    public ResponseEntity<StandKeypadState> completeStandKeypadOrder(UUID id, UUID orderId) {
        StandKeypadService.KeypadStateView view = keypadService.completeOrder(id, orderId);
        return ResponseEntity.ok(mapper.toStandKeypadState(view));
    }
}

