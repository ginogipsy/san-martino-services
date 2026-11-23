package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.StandsApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.*;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class StandsController implements StandsApi {

    private final StandService service;
    private final StandMapper mapper;
    private final com.ginogipsy.sanmartino.stands.service.StandKeypadService keypadService;

    @org.springframework.beans.factory.annotation.Autowired
    public StandsController(
            StandService service,
            StandMapper mapper,
            com.ginogipsy.sanmartino.stands.service.StandKeypadService keypadService
    ) {
        this.service = service;
        this.mapper = mapper;
        this.keypadService = keypadService;
    }

    @Override
    public ResponseEntity<List<StandSummary>> listStands() {
        java.util.Map<UUID, com.ginogipsy.sanmartino.stands.service.StandKeypadService.StandQueueSnapshot> snapshots =
                keypadService != null ? keypadService.loadQueueSnapshotsByStandId() : java.util.Map.of();
        return ResponseEntity.ok(
                service.findAll().stream()
                        .map(s -> mapper.toSummary(
                                s,
                                snapshots.getOrDefault(s.getId(), com.ginogipsy.sanmartino.stands.service.StandKeypadService.StandQueueSnapshot.EMPTY)
                        ))
                        .toList()
        );
    }

    @Override
    public ResponseEntity<List<StandSearchResult>> searchStands(
            String q,
            MenuKind kind,
            Boolean onlyAvailable,
            Double lat,
            Double lng
    ) {
        com.ginogipsy.sanmartino.stands.domain.MenuKind domainKind =
                kind == null ? null : com.ginogipsy.sanmartino.stands.domain.MenuKind.valueOf(kind.getValue());
        boolean availableOnly = Boolean.TRUE.equals(onlyAvailable);
        List<StandSearchResult> results = service.searchStands(q, domainKind, availableOnly, lat, lng)
                .stream()
                .map(mapper::toSearchResult)
                .toList();
        return ResponseEntity.ok(results);
    }

    @Override
    public ResponseEntity<StandDetail> createStand(StandCreate body) {
        StandEntity saved = service.create(mapper.fromCreate(body));
        return ResponseEntity
                .created(URI.create("/v1/stands/" + saved.getId()))
                .body(mapper.toDetail(saved));
    }

    @Override
    public ResponseEntity<StandDetail> getStand(UUID id) {
        return ResponseEntity.ok(mapper.toDetail(service.findById(id)));
    }

    @Override
    public ResponseEntity<StandDetail> updateStand(UUID id, StandUpdate body) {
        StandEntity updated = service.update(id, mapper.fromUpdate(body));
        return ResponseEntity.ok(mapper.toDetail(updated));
    }

    @Override
    public ResponseEntity<Void> deleteStand(UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
