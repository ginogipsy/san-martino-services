package com.ginogipsy.sanmartino.events;

import com.ginogipsy.sanmartino.events.api.generated.model.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EventsApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void listEvents_returnsSeededArceEditions() {
        ResponseEntity<Event[]> response = restTemplate.getForEntity("/v1/events", Event[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(5);
    }

    @Test
    void getEvent_returnsArce2026() {
        ResponseEntity<Event> response = restTemplate.getForEntity(
                "/v1/events/11111111-1111-1111-1111-000000002026",
                Event.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Le cantine di San Martino 2026");
    }

    @Test
    void getEvent_unknown_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/v1/events/99999999-9999-9999-9999-999999999999",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEvent_returns201AndPersists() {
        String body = """
                {
                  "name": "Test Edition",
                  "place": "Arce (FR)",
                  "startDate": "2027-11-13",
                  "endDate": "2027-11-14",
                  "description": { "it": "Descrizione IT", "en": "EN description" }
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Event> response = restTemplate.postForEntity("/v1/events", request, Event.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
    }
}