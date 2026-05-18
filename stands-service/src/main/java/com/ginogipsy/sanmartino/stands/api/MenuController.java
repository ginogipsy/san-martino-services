package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.api.generated.MenuApi;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItem;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItemCreate;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuItemUpdate;
import com.ginogipsy.sanmartino.stands.api.generated.model.MenuKind;
import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.service.StandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class MenuController implements MenuApi {

    private final StandService service;
    private final StandMapper mapper;

    public MenuController(StandService service, StandMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<MenuItem>> listMenuItems(UUID id, MenuKind kind) {
        com.ginogipsy.sanmartino.stands.domain.MenuKind domainKind = kind == null
                ? null
                : com.ginogipsy.sanmartino.stands.domain.MenuKind.valueOf(kind.getValue());
        List<MenuItemEntity> items = service.listMenuItems(id, domainKind);
        return ResponseEntity.ok(items.stream().map(mapper::toMenuItem).toList());
    }

    @Override
    public ResponseEntity<MenuItem> addMenuItem(UUID id, MenuItemCreate body) {
        MenuItemEntity saved = service.addMenuItem(id, mapper.fromMenuItemCreate(body));
        return ResponseEntity
                .created(URI.create("/v1/stands/" + id + "/menu-items/" + saved.getId()))
                .body(mapper.toMenuItem(saved));
    }

    @Override
    public ResponseEntity<MenuItem> updateMenuItem(UUID id, UUID itemId, MenuItemUpdate body) {
        MenuItemEntity updated = service.updateMenuItem(id, itemId, mapper.fromMenuItemUpdate(body));
        return ResponseEntity.ok(mapper.toMenuItem(updated));
    }

    @Override
    public ResponseEntity<Void> deleteMenuItem(UUID id, UUID itemId) {
        service.deleteMenuItem(id, itemId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
