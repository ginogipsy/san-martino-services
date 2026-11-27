package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.model.*;
import com.ginogipsy.sanmartino.stands.domain.FestivalReviewEntity;
import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.domain.StandOwnerEntity;
import com.ginogipsy.sanmartino.stands.service.StandKeypadService;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class StandMapper {

    private final StandKeypadService keypadService;

    public StandMapper() {
        this(null);
    }

    @Autowired
    public StandMapper(StandKeypadService keypadService) {
        this.keypadService = keypadService;
    }

    public StandEntity fromCreate(StandCreate dto) {
        StandEntity entity = new StandEntity(
                null,
                dto.getNumber(),
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getFirstParticipationYear(),
                dto.getLatitude(),
                dto.getLongitude()
        );
        if (dto.getEventId() != null) {
            entity.setActiveEventId(dto.getEventId());
        }
        return entity;
    }

    public StandEntity fromUpdate(StandUpdate dto) {
        StandEntity entity = new StandEntity(
                null,
                dto.getNumber(),
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getFirstParticipationYear(),
                dto.getLatitude(),
                dto.getLongitude()
        );
        if (dto.getEventId() != null) {
            entity.setActiveEventId(dto.getEventId());
        }
        return entity;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    public StandSummary toSummary(StandEntity entity) {
        StandKeypadService.StandQueueSnapshot snap = resolveQueueSnapshot(entity.getId());
        return toSummary(entity, snap);
    }

    public StandSummary toSummary(StandEntity entity, StandKeypadService.StandQueueSnapshot snap) {
        StandSummary dto = new StandSummary(
                entity.getId(),
                entity.getNumber(),
                entity.getName(),
                new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn()),
                entity.getFirstParticipationYear(),
                entity.getLatitude(),
                entity.getLongitude()
        );
        dto.setEventId(entity.getActiveEventId());
        dto.setParticipatingEventIds(extractParticipatingEventIds(entity));
        if (snap != null) {
            dto.setActiveOrdersCount(snap.activeOrdersCount());
            dto.setCurrentServingOrderNumber(snap.currentServingOrderNumber());
            dto.setEstimatedWaitMinutes(snap.estimatedWaitMinutes());
        }
        applyAuditing(entity, dto::setCreatedAt, dto::setUpdatedAt);
        return dto;
    }

    public StandDetail toDetail(StandEntity entity) {
        List<Owner> owners = entity.getOwners().stream().map(this::toOwner).toList();
        List<MenuItem> items = entity.getMenuItems().stream().map(this::toMenuItem).toList();
        StandDetail dto = new StandDetail(
                entity.getId(),
                entity.getNumber(),
                entity.getName(),
                new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn()),
                entity.getFirstParticipationYear(),
                entity.getLatitude(),
                entity.getLongitude(),
                owners,
                items
        );
        dto.setEventId(entity.getActiveEventId());
        dto.setParticipatingEventIds(extractParticipatingEventIds(entity));
        StandKeypadService.StandQueueSnapshot snap = resolveQueueSnapshot(entity.getId());
        if (snap != null) {
            dto.setActiveOrdersCount(snap.activeOrdersCount());
            dto.setCurrentServingOrderNumber(snap.currentServingOrderNumber());
            dto.setEstimatedWaitMinutes(snap.estimatedWaitMinutes());
        }
        applyAuditing(entity, dto::setCreatedAt, dto::setUpdatedAt);
        return dto;
    }

    private List<UUID> extractParticipatingEventIds(StandEntity entity) {
        if (entity.getParticipations() == null || entity.getParticipations().isEmpty()) {
            return List.of(entity.getActiveEventId());
        }
        return entity.getParticipations().stream()
                .map(com.ginogipsy.sanmartino.stands.domain.StandEventParticipationEntity::getEventId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private StandKeypadService.StandQueueSnapshot resolveQueueSnapshot(UUID standId) {
        if (keypadService == null || standId == null) {
            return StandKeypadService.StandQueueSnapshot.EMPTY;
        }
        Map<UUID, StandKeypadService.StandQueueSnapshot> map = keypadService.loadQueueSnapshotsByStandId();
        return map.getOrDefault(standId, StandKeypadService.StandQueueSnapshot.EMPTY);
    }

    public AccessibleKeypadStand toAccessibleKeypadStand(StandKeypadService.AccessibleStandView view) {
        List<StandStaffRole> roles = view.permissions().stream()
                .map(r -> StandStaffRole.valueOf(r.name()))
                .toList();
        return new AccessibleKeypadStand(
                view.standId(),
                view.standNumber(),
                view.standName(),
                roles,
                view.activeOrdersCount(),
                view.currentServingOrderNumber()
        );
    }

    public StandKeypadState toStandKeypadState(StandKeypadService.KeypadStateView view) {
        List<StandStaffRole> userPermissions = view.userPermissions().stream()
                .map(r -> StandStaffRole.valueOf(r.name()))
                .toList();

        List<StandKeypadSlotView> slots = view.slots().stream()
                .map(s -> {
                    StandKeypadSlotView slotDto = new StandKeypadSlotView(
                            s.slotNumber(),
                            s.availablePlates(),
                            s.pendingToCookCount(),
                            s.currentDeliveryOrderRequiredCount(),
                            s.currentDeliveryOrderCookedCount(),
                            s.currentDeliveryOrderDeliveredCount(),
                            s.kitchenLedOn(),
                            DeliveryLedState.valueOf(s.deliveryLedState().name())
                    );
                    slotDto.setMenuItemId(s.menuItemId());
                    slotDto.setMenuItemName(s.menuItemName());
                    if (s.kind() != null) {
                        slotDto.setKind(MenuKind.valueOf(s.kind().name()));
                    }
                    return slotDto;
                })
                .toList();

        List<StandOrderView> activeOrders = view.activeOrders().stream()
                .map(this::toStandOrderView)
                .toList();

        List<StandOrderView> recentDeliveredOrders = view.recentDeliveredOrders().stream()
                .map(this::toStandOrderView)
                .toList();

        List<StandStaffPermissionView> staffPermissions = view.staffPermissions().stream()
                .map(p -> {
                    StandStaffPermissionView permDto = new StandStaffPermissionView(
                            p.id(),
                            p.standId(),
                            p.username(),
                            StandStaffRole.valueOf(p.permission().name()),
                            toOffsetDateTime(p.createdAt())
                    );
                    permDto.setGrantedBy(p.grantedBy());
                    return permDto;
                })
                .toList();

        StandKeypadState dto = new StandKeypadState(
                view.standId(),
                view.standNumber(),
                view.standName(),
                view.nextOrderNumber(),
                view.lastIssuedOrderNumber(),
                view.oldestKitchenOrderNumber(),
                view.currentDeliveryOrderNumber(),
                view.lastDeliveredOrderNumber(),
                view.activeOrdersCount(),
                view.totalPendingKitchenPortions(),
                userPermissions,
                slots,
                activeOrders,
                recentDeliveredOrders,
                staffPermissions
        );
        dto.setDeviceToken(view.deviceToken());
        dto.setLastCashierSeenAt(toOffsetDateTime(view.lastCashierSeenAt()));
        dto.setLastKitchenSeenAt(toOffsetDateTime(view.lastKitchenSeenAt()));
        dto.setLastDeliverySeenAt(toOffsetDateTime(view.lastDeliverySeenAt()));
        return dto;
    }

    private StandOrderView toStandOrderView(StandKeypadService.OrderView o) {
        List<StandOrderItemView> items = o.items().stream()
                .map(it -> {
                    StandOrderItemView itemDto = new StandOrderItemView(
                            it.id(),
                            it.slotNumber(),
                            it.menuItemName(),
                            MenuKind.valueOf(it.kind().name()),
                            it.orderedQuantity(),
                            it.cookedQuantity(),
                            it.deliveredQuantity()
                    );
                    itemDto.setMenuItemId(it.menuItemId());
                    return itemDto;
                })
                .toList();

        StandOrderView dto = new StandOrderView(
                o.id(),
                o.standId(),
                o.orderNumber(),
                StandOrderStatus.valueOf(o.status().name()),
                toOffsetDateTime(o.createdAt()),
                items
        );
        dto.setCreatedBy(o.createdBy());
        dto.setReadyAt(toOffsetDateTime(o.readyAt()));
        dto.setDeliveredAt(toOffsetDateTime(o.deliveredAt()));
        return dto;
    }

    public StandSearchResult toSearchResult(StandService.StandSearchHit hit) {
        List<MenuItem> matchedItems = hit.matchedItems().stream().map(this::toMenuItem).toList();
        return new StandSearchResult(
                toSummary(hit.stand()),
                matchedItems,
                hit.score()
        );
    }

    public StandAnalyticsSummary toAnalyticsSummary(StandService.AnalyticsSummaryView view) {
        List<SearchQueryStat> topSearches = view.topSearchedQueries().stream()
                .map(this::toSearchQueryStat)
                .toList();
        List<SearchQueryStat> zeroResults = view.zeroResultQueries().stream()
                .map(this::toSearchQueryStat)
                .toList();
        List<MenuItemClickStat> topItems = view.topClickedMenuItems().stream()
                .map(it -> new MenuItemClickStat(
                        it.menuItemId(),
                        it.menuItemName(),
                        MenuKind.valueOf(it.kind().name()),
                        it.standId(),
                        it.standNumber(),
                        it.standName(),
                        it.clickCount()
                ))
                .toList();
        List<StandClickStat> topStands = view.topClickedStands().stream()
                .map(st -> new StandClickStat(
                        st.standId(),
                        st.standNumber(),
                        st.standName(),
                        st.interactionCount()
                ))
                .toList();
        return new StandAnalyticsSummary(
                view.totalSearches(),
                view.totalInteractions(),
                topSearches,
                zeroResults,
                topItems,
                topStands
        );
    }

    private SearchQueryStat toSearchQueryStat(StandService.SearchQueryStatView stat) {
        SearchQueryStat dto = new SearchQueryStat(
                stat.query(),
                stat.searchCount(),
                stat.avgResultsCount()
        );
        if (stat.lastSearchedAt() != null) {
            dto.setLastSearchedAt(OffsetDateTime.ofInstant(stat.lastSearchedAt(), ZoneOffset.UTC));
        }
        return dto;
    }

    public FestivalReview toReview(FestivalReviewEntity entity) {
        FestivalReview dto = new FestivalReview(
                entity.getId(),
                ReviewTargetType.valueOf(entity.getTargetType().name()),
                entity.getTargetId(),
                entity.getTargetName(),
                entity.getAuthorUserId(),
                entity.getAuthorUsername(),
                entity.getRating(),
                entity.getComment(),
                ReviewStatus.valueOf(entity.getStatus().name()),
                OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC)
        );
        dto.setStandId(entity.getStandId());
        dto.setModeratedBy(entity.getModeratedBy());
        dto.setModerationNote(entity.getModerationNote());
        if (entity.getModeratedAt() != null) {
            dto.setModeratedAt(OffsetDateTime.ofInstant(entity.getModeratedAt(), ZoneOffset.UTC));
        }
        return dto;
    }

    public MenuItemEntity fromMenuItemCreate(MenuItemCreate dto) {
        return new MenuItemEntity(
                null,
                dto.getEventId(),
                dto.getCanonicalItemId(),
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getAvailablePlates(),
                com.ginogipsy.sanmartino.stands.domain.MenuKind.valueOf(dto.getKind().getValue()),
                dto.getKeywords()
        );
    }

    public MenuItemEntity fromMenuItemUpdate(MenuItemUpdate dto) {
        return new MenuItemEntity(
                null,
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getAvailablePlates(),
                com.ginogipsy.sanmartino.stands.domain.MenuKind.valueOf(dto.getKind().getValue()),
                dto.getKeywords()
        );
    }

    public MenuItem toMenuItem(MenuItemEntity entity) {
        MenuItem dto = new MenuItem(
                entity.getId(),
                entity.getStand().getId(),
                entity.getName(),
                new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn()),
                entity.getAvailablePlates(),
                MenuKind.valueOf(entity.getKind().name()),
                entity.getKeywords()
        );
        dto.setEventId(entity.getEventId());
        dto.setCanonicalItemId(entity.getCanonicalItemId());
        return dto;
    }

    public StandOwnerEntity fromOwnerCreate(OwnerCreate dto) {
        return new StandOwnerEntity(
                null,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getEmail(),
                dto.getPhone(),
                com.ginogipsy.sanmartino.stands.domain.OwnerRole.valueOf(dto.getRole().getValue())
        );
    }

    public Owner toOwner(StandOwnerEntity entity) {
        Owner dto = new Owner(
                entity.getId(),
                entity.getStand().getId(),
                entity.getFirstName(),
                entity.getLastName(),
                OwnerRole.valueOf(entity.getRole().name())
        );
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        return dto;
    }

    private void applyAuditing(
            StandEntity entity,
            Consumer<OffsetDateTime> setCreatedAt,
            Consumer<OffsetDateTime> setUpdatedAt
    ) {
        if (entity.getCreatedAt() != null) {
            setCreatedAt.accept(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        }
        if (entity.getUpdatedAt() != null) {
            setUpdatedAt.accept(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
        }
    }
}

