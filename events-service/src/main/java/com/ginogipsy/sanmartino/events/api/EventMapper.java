package com.ginogipsy.sanmartino.events.api;

import com.ginogipsy.sanmartino.events.api.generated.model.Event;
import com.ginogipsy.sanmartino.events.api.generated.model.EventCreate;
import com.ginogipsy.sanmartino.events.api.generated.model.EventStatus;
import com.ginogipsy.sanmartino.events.api.generated.model.EventUpdate;
import com.ginogipsy.sanmartino.events.api.generated.model.LocalizedText;
import com.ginogipsy.sanmartino.events.domain.EventEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class EventMapper {

    private final Clock clock;

    public EventMapper(Clock clock) {
        this.clock = clock;
    }

    public EventEntity fromCreate(EventCreate dto) {
        return new EventEntity(
                null,
                dto.getName(),
                dto.getPlace(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn()
        );
    }

    public EventEntity fromUpdate(EventUpdate dto) {
        return new EventEntity(
                null,
                dto.getName(),
                dto.getPlace(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getDescription().getIt(),
                dto.getDescription().getEn()
        );
    }

    public Event toApi(EventEntity entity) {
        LocalizedText description = new LocalizedText(entity.getDescriptionIt(), entity.getDescriptionEn());
        EventStatus status = computeStatus(entity);
        Event event = new Event(
                entity.getId(),
                entity.getName(),
                entity.getPlace(),
                entity.getStartDate(),
                entity.getEndDate(),
                description,
                status
        );
        if (entity.getCreatedAt() != null) {
            event.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
        }
        if (entity.getUpdatedAt() != null) {
            event.setUpdatedAt(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
        }
        return event;
    }

    private EventStatus computeStatus(EventEntity entity) {
        LocalDate today = LocalDate.now(clock);
        return entity.getEndDate().isBefore(today) ? EventStatus.PAST : EventStatus.UPCOMING;
    }
}
