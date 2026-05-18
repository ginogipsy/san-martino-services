package com.ginogipsy.sanmartino.stands.service;

import com.ginogipsy.sanmartino.stands.domain.MenuItemEntity;
import com.ginogipsy.sanmartino.stands.domain.MenuKind;
import com.ginogipsy.sanmartino.stands.domain.StandEntity;
import com.ginogipsy.sanmartino.stands.domain.StandOwnerEntity;
import com.ginogipsy.sanmartino.stands.domain.StandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StandService {

    private final StandRepository repository;

    public StandService(StandRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StandEntity> findAll() {
        return repository.findAllByOrderByNumberAsc();
    }

    @Transactional(readOnly = true)
    public StandEntity findById(UUID id) {
        StandEntity stand = repository.findById(id).orElseThrow(() -> new StandNotFoundException(id));
        // Materialize lazy collections so the mapper can read them outside the session
        stand.getOwners().size();
        stand.getMenuItems().forEach(item -> item.getKeywords().size());
        return stand;
    }

    @Transactional
    public StandEntity create(StandEntity stand) {
        stand.setId(UUID.randomUUID());
        return repository.save(stand);
    }

    @Transactional
    public StandEntity update(UUID id, StandEntity update) {
        StandEntity current = findById(id);
        current.setNumber(update.getNumber());
        current.setName(update.getName());
        current.setDescriptionIt(update.getDescriptionIt());
        current.setDescriptionEn(update.getDescriptionEn());
        current.setFirstParticipationYear(update.getFirstParticipationYear());
        current.setLatitude(update.getLatitude());
        current.setLongitude(update.getLongitude());
        return current;
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new StandNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MenuItemEntity> listMenuItems(UUID standId, MenuKind kindFilter) {
        StandEntity stand = findById(standId);
        List<MenuItemEntity> items = stand.getMenuItems();
        if (kindFilter == null) return items;
        return items.stream().filter(it -> it.getKind() == kindFilter).toList();
    }

    @Transactional
    public MenuItemEntity addMenuItem(UUID standId, MenuItemEntity item) {
        StandEntity stand = findById(standId);
        item.setId(UUID.randomUUID());
        stand.addMenuItem(item);
        return item;
    }

    @Transactional
    public MenuItemEntity updateMenuItem(UUID standId, UUID itemId, MenuItemEntity update) {
        StandEntity stand = findById(standId);
        MenuItemEntity current = stand.getMenuItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new MenuItemNotFoundException(standId, itemId));
        current.setName(update.getName());
        current.setDescriptionIt(update.getDescriptionIt());
        current.setDescriptionEn(update.getDescriptionEn());
        current.setAvailablePlates(update.getAvailablePlates());
        current.setKind(update.getKind());
        current.setKeywords(update.getKeywords());
        return current;
    }

    @Transactional
    public void deleteMenuItem(UUID standId, UUID itemId) {
        StandEntity stand = findById(standId);
        MenuItemEntity item = stand.getMenuItems().stream()
                .filter(it -> it.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new MenuItemNotFoundException(standId, itemId));
        stand.removeMenuItem(item);
    }

    @Transactional(readOnly = true)
    public List<StandOwnerEntity> listOwners(UUID standId) {
        return findById(standId).getOwners();
    }

    @Transactional
    public StandOwnerEntity addOwner(UUID standId, StandOwnerEntity owner) {
        StandEntity stand = findById(standId);
        owner.setId(UUID.randomUUID());
        stand.addOwner(owner);
        return owner;
    }

    @Transactional
    public void deleteOwner(UUID standId, UUID ownerId) {
        StandEntity stand = findById(standId);
        StandOwnerEntity owner = stand.getOwners().stream()
                .filter(o -> o.getId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new OwnerNotFoundException(standId, ownerId));
        stand.removeOwner(owner);
    }
}
