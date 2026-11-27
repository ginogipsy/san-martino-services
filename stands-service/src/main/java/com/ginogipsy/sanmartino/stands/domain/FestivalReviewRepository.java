package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FestivalReviewRepository extends JpaRepository<FestivalReviewEntity, UUID> {

    @Query("""
            SELECT r FROM FestivalReviewEntity r
            WHERE (:targetType IS NULL OR r.targetType = :targetType)
              AND (:targetId IS NULL OR r.targetId = :targetId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    List<FestivalReviewEntity> findFiltered(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("status") ReviewStatus status
    );

    @Query("""
            SELECT r FROM FestivalReviewEntity r
            WHERE r.targetType = :targetType
              AND r.targetId IN :targetIds
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    List<FestivalReviewEntity> findFilteredByTargetIds(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetIds") java.util.Collection<UUID> targetIds,
            @Param("status") ReviewStatus status
    );

    @Query("""
            SELECT r.targetId AS targetId,
                   AVG(r.rating) AS avgRating,
                   COUNT(r) AS reviewCount
            FROM FestivalReviewEntity r
            WHERE r.status = com.ginogipsy.sanmartino.stands.domain.ReviewStatus.APPROVED
              AND r.targetType = com.ginogipsy.sanmartino.stands.domain.ReviewTargetType.MENU_ITEM
            GROUP BY r.targetId
            """)
    List<TargetRatingAggregation> findApprovedMenuItemRatings();

    interface TargetRatingAggregation {
        UUID getTargetId();

        double getAvgRating();

        long getReviewCount();
    }
}
