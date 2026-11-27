package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.AnalyticsApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandAnalyticsSummary;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandInteractionCreate;
import com.ginogipsy.sanmartino.stands.domain.StandInteractionType;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController implements AnalyticsApi {

    private final StandService service;
    private final StandMapper mapper;

    public AnalyticsController(StandService service, StandMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> recordInteraction(StandInteractionCreate body) {
        StandInteractionType type = StandInteractionType.valueOf(body.getInteractionType().getValue());
        service.recordInteraction(
                body.getStandId(),
                body.getMenuItemId(),
                type,
                body.getSearchQuery()
        );
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Override
    public ResponseEntity<StandAnalyticsSummary> getAnalyticsSummary(Integer limit) {
        int effectiveLimit = limit != null ? limit : 10;
        return ResponseEntity.ok(mapper.toAnalyticsSummary(service.getAnalyticsSummary(effectiveLimit)));
    }
}
