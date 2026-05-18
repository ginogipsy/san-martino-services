package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.model.LocalizedText;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItem;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItemCreate;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItemUpdate;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuKind;
import com.ginogipsy.sanmartino.stands.api.generated.model.Owner;
import com.ginogipsy.sanmartino.stands.api.generated.model.OwnerCreate;
import com.ginogipsy.sanmartino.stands.api.generated.model.OwnerRole;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandCreate;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandDetail;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandSummary;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandUpdate;
import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.domain.StandOwnerEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

@Component
public class StandMapper {

    public StandEntity fromCreate(StandCreate dto) {
        return new StandEntity(
                null,
                dto.getNumber(),
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getFirstParticipationYear(),
                dto.getLatitude(),
                dto.getLongitude()
        );
    }

    public StandEntity fromUpdate(StandUpdate dto) {
        return new StandEntity(
                null,
                dto.getNumber(),
                dto.getName(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn(),
                dto.getFirstParticipationYear(),
                dto.getLatitude(),
                dto.getLongitude()
        );
    }

    public StandSummary toSummary(StandEntity entity) {
        StandSummary dto = new StandSummary(
                entity.getId(),
                entity.getNumber(),
                entity.getName(),
                new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn()),
                entity.getFirstParticipationYear(),
                entity.getLatitude(),
                entity.getLongitude()
        );
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
        applyAuditing(entity, dto::setCreatedAt, dto::setUpdatedAt);
        return dto;
    }

    public MenuItemEntity fromMenuItemCreate(MenuItemCreate dto) {
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
        return new MenuItem(
                entity.getId(),
                entity.getStand().getId(),
                entity.getName(),
                new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn()),
                entity.getAvailablePlates(),
                MenuKind.valueOf(entity.getKind().name()),
                entity.getKeywords()
        );
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
