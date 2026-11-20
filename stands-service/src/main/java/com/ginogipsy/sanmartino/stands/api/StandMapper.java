package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.model.*;
import com.ginogipsy.sanmartino.stands.domain.FestivalReviewEntity;
import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.domain.StandOwnerEntity;
import com.ginogipsy.sanmartino.stands.service.StandService;
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
