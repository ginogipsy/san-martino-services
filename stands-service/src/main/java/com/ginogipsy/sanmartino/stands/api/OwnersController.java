package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.OwnersApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.Owner;
import com.ginogipsy.sanmartino.stands.api.generated.model.OwnerCreate;
import com.ginogipsy.sanmartino.stands.domain.StandOwnerEntity;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class OwnersController implements OwnersApi {

    private final StandService service;
    private final StandMapper mapper;

    public OwnersController(StandService service, StandMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<Owner>> listOwners(UUID id) {
        List<StandOwnerEntity> owners = service.listOwners(id);
        return ResponseEntity.ok(owners.stream().map(mapper::toOwner).toList());
    }

    @Override
    public ResponseEntity<Owner> addOwner(UUID id, OwnerCreate body) {
        StandOwnerEntity saved = service.addOwner(id, mapper.fromOwnerCreate(body));
        return ResponseEntity
                .created(URI.create("/v1/stands/" + id + "/owners/" + saved.getId()))
                .body(mapper.toOwner(saved));
    }

    @Override
    public ResponseEntity<Void> deleteOwner(UUID id, UUID ownerId) {
        service.deleteOwner(id, ownerId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
