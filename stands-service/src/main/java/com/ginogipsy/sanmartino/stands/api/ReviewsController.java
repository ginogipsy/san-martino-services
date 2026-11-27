package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.ReviewsApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.*;
import com.ginogipsy.sanmartino.stands.domain.FestivalReviewEntity;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ReviewsController implements ReviewsApi {

    private final StandService service;
    private final StandMapper mapper;

    public ReviewsController(StandService service, StandMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<FestivalReview>> listReviews(
            ReviewTargetType targetType,
            UUID targetId,
            ReviewStatus status
    ) {
        com.ginogipsy.sanmartino.stands.domain.ReviewTargetType domainType = targetType == null
                ? null
                : com.ginogipsy.sanmartino.stands.domain.ReviewTargetType.valueOf(targetType.getValue());
        com.ginogipsy.sanmartino.stands.domain.ReviewStatus domainStatus = status == null
                ? null
                : com.ginogipsy.sanmartino.stands.domain.ReviewStatus.valueOf(status.getValue());

        List<FestivalReview> reviews = service.listReviews(domainType, targetId, domainStatus)
                .stream()
                .map(mapper::toReview)
                .toList();
        return ResponseEntity.ok(reviews);
    }

    @Override
    public ResponseEntity<FestivalReview> createReview(FestivalReviewCreate body) {
        com.ginogipsy.sanmartino.stands.domain.ReviewTargetType domainType =
                com.ginogipsy.sanmartino.stands.domain.ReviewTargetType.valueOf(body.getTargetType().getValue());
        FestivalReviewEntity saved = service.createReview(
                domainType,
                body.getTargetId(),
                body.getStandId(),
                body.getTargetName(),
                body.getAuthorUsername(),
                body.getRating(),
                body.getComment()
        );
        return ResponseEntity
                .created(URI.create("/v1/stands/reviews/" + saved.getId()))
                .body(mapper.toReview(saved));
    }

    @Override
    public ResponseEntity<FestivalReview> moderateReview(UUID reviewId, FestivalReviewModeration body) {
        com.ginogipsy.sanmartino.stands.domain.ReviewStatus domainStatus =
                com.ginogipsy.sanmartino.stands.domain.ReviewStatus.valueOf(body.getStatus().getValue());
        FestivalReviewEntity updated = service.moderateReview(
                reviewId,
                domainStatus,
                body.getModerationNote()
        );
        return ResponseEntity.ok(mapper.toReview(updated));
    }
}
