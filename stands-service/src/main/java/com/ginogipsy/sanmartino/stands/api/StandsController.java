package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.StandsApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandCreate;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandDetail;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandSummary;
import com.ginogipsy.sanmartino.stands.api.generated.model.StandUpdate;
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

    public StandsController(StandService service, StandMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<StandSummary>> listStands() {
        return ResponseEntity.ok(service.findAll().stream().map(mapper::toSummary).toList());
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
