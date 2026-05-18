package com.ginogipsy.sanmartino.events.service;

import com.ginogipsy.sanmartino.events.domain.EventEntity;
import com.ginogipsy.sanmartino.events.domain.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository repository;
    private final Clock clock;

    public EventService(EventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EventEntity> findUpcoming() {
        return repository.findAllByEndDateGreaterThanEqualOrderByStartDateAsc(today());
    }

    @Transactional(readOnly = true)
    public List<EventEntity> findPast() {
        return repository.findAllByEndDateLessThanOrderByStartDateDesc(today());
    }

    @Transactional(readOnly = true)
    public EventEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    @Transactional
    public EventEntity create(EventEntity event) {
        event.setId(UUID.randomUUID());
        return repository.save(event);
    }

    @Transactional
    public EventEntity update(UUID id, EventEntity update) {
        EventEntity current = findById(id);
        current.setName(update.getName());
        current.setPlace(update.getPlace());
        current.setStartDate(update.getStartDate());
        current.setEndDate(update.getEndDate());
        current.setDescriptionIt(update.getDescriptionIt());
        current.setDescriptionEn(update.getDescriptionEn());
        return current;
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EventNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
