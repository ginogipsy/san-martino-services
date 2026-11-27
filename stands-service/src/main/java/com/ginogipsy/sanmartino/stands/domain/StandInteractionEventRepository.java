package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StandInteractionEventRepository extends JpaRepository<StandInteractionEventEntity, UUID> {

    @Query("""
            SELECT e.menuItemId AS menuItemId,
                   COUNT(e) AS clickCount
            FROM StandInteractionEventEntity e
            WHERE e.menuItemId IS NOT NULL
              AND e.interactionType = com.ginogipsy.sanmartino.stands.domain.StandInteractionType.MENU_ITEM_CLICK
            GROUP BY e.menuItemId
            ORDER BY COUNT(e) DESC
            """)
    List<MenuItemClickAggregation> findTopClickedMenuItems(Pageable pageable);

    @Query("""
            SELECT e.menuItemId AS menuItemId,
                   COUNT(e) AS clickCount
            FROM StandInteractionEventEntity e
            WHERE e.menuItemId IS NOT NULL
              AND e.interactionType = com.ginogipsy.sanmartino.stands.domain.StandInteractionType.MENU_ITEM_CLICK
            GROUP BY e.menuItemId
            """)
    List<MenuItemClickAggregation> findAllMenuItemClickCounts();

    @Query("""
            SELECT e.standId AS standId,
                   COUNT(e) AS interactionCount
            FROM StandInteractionEventEntity e
            GROUP BY e.standId
            ORDER BY COUNT(e) DESC
            """)
    List<StandClickAggregation> findTopClickedStands(Pageable pageable);

    interface MenuItemClickAggregation {
        UUID getMenuItemId();

        long getClickCount();
    }

    interface StandClickAggregation {
        UUID getStandId();

        long getInteractionCount();
    }
}
