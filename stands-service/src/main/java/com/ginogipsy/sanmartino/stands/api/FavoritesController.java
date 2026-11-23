package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.FavoritesApi;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class FavoritesController implements FavoritesApi {

    private final StandService service;

    public FavoritesController(StandService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<UUID>> listFavoriteStandIds() {
        return ResponseEntity.ok(service.listFavoriteStandIds());
    }

    @Override
    public ResponseEntity<Void> addFavoriteStand(UUID standId) {
        service.addFavoriteStand(standId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFavoriteStand(UUID standId) {
        service.removeFavoriteStand(standId);
        return ResponseEntity.noContent().build();
    }
}
