package com.ginogipsy.sanmartino.events.api;

import com.ginogipsy.sanmartino.events.api.generated.EventsApi;
import com.ginogipsy.sanmartino.events.api.generated.model.Event;
import com.ginogipsy.sanmartino.events.api.generated.model.EventCreate;
import com.ginogipsy.sanmartino.events.api.generated.model.EventStatus;
import com.ginogipsy.sanmartino.events.api.generated.model.EventUpdate;
import com.ginogipsy.sanmartino.events.domain.EventEntity;
import com.ginogipsy.sanmartino.events.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class EventsController implements EventsApi {

    private final EventService eventService;
    private final EventMapper eventMapper;

    public EventsController(EventService eventService, EventMapper eventMapper) {
        this.eventService = eventService;
        this.eventMapper = eventMapper;
    }

    @Override
    public ResponseEntity<List<Event>> listEvents(EventStatus status) {
        List<EventEntity> entities = switch (status == null ? null : status) {
            case UPCOMING -> eventService.findUpcoming();
            case PAST -> eventService.findPast();
            case null -> eventService.findAll();
        };
        return ResponseEntity.ok(entities.stream().map(eventMapper::toApi).toList());
    }

    @Override
    public ResponseEntity<Event> createEvent(EventCreate eventCreate) {
        EventEntity saved = eventService.create(eventMapper.fromCreate(eventCreate));
        Event body = eventMapper.toApi(saved);
        return ResponseEntity
                .created(URI.create("/v1/events/" + saved.getId()))
                .body(body);
    }

    @Override
    public ResponseEntity<Event> getEvent(UUID id) {
        return ResponseEntity.ok(eventMapper.toApi(eventService.findById(id)));
    }

    @Override
    public ResponseEntity<Event> updateEvent(UUID id, EventUpdate eventUpdate) {
        EventEntity updated = eventService.update(id, eventMapper.fromUpdate(eventUpdate));
        return ResponseEntity.ok(eventMapper.toApi(updated));
    }

    @Override
    public ResponseEntity<Void> deleteEvent(UUID id) {
        eventService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
