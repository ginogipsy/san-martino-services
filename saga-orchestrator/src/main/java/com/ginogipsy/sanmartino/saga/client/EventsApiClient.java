package com.ginogipsy.sanmartino.saga.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Thin client per events-service. Solo i due metodi che ci servono per la saga
 * "CreateEventWithStands": create e delete (per la compensation).
 */
@Component
public class EventsApiClient {

    private final RestClient restClient;

    public EventsApiClient(@Qualifier("eventsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public record CreatedEvent(UUID id, String name) {}

    public CreatedEvent createEvent(EventCreateRequest request) {
        Map<String, Object> body = Map.of(
                "name", request.name(),
                "place", request.place(),
                "startDate", request.startDate().toString(),
                "endDate", request.endDate().toString(),
                "description", Map.of(
                        "it", request.descriptionIt(),
                        "en", request.descriptionEn()
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Empty response from events-service");
        }
        UUID id = UUID.fromString((String) response.get("id"));
        String name = (String) response.get("name");
        return new CreatedEvent(id, name);
    }

    public void deleteEvent(UUID eventId) {
        restClient.delete()
                .uri("/v1/events/{id}", eventId)
                .retrieve()
                .toBodilessEntity();
    }

    public record EventCreateRequest(
            String name,
            String place,
            LocalDate startDate,
            LocalDate endDate,
            String descriptionIt,
            String descriptionEn
    ) {}
}
