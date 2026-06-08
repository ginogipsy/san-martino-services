package com.ginogipsy.sanmartino.saga.service;

import com.ginogipsy.sanmartino.saga.client.EventsApiClient;
import com.ginogipsy.sanmartino.saga.client.StandsApiClient;
import com.ginogipsy.sanmartino.saga.domain.ParticipationEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaInstanceEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaRepository;
import com.ginogipsy.sanmartino.saga.domain.SagaStatus;
import com.ginogipsy.sanmartino.saga.domain.SagaStepEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaStepStatus;
import com.ginogipsy.sanmartino.saga.event.SagaEvent;
import com.ginogipsy.sanmartino.saga.event.SagaEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Saga orchestrator per la story "CreateEventWithStands".
 *
 * Steps:
 * 1. createEvent  -- POST events-service /v1/events
 * 2. validateStands -- per ogni standId, GET stands-service /v1/stands/{id}
 * 3. registerParticipations -- INSERT locale in tabella participations
 *
 * Compensation se step 2 o 3 falliscono: DELETE dell'event creato (step 1).
 */
@Service
public class CreateEventWithStandsOrchestrator {

    private static final String SAGA_TYPE = "CreateEventWithStands";
    private static final Logger log = LoggerFactory.getLogger(CreateEventWithStandsOrchestrator.class);

    private final SagaRepository sagaRepository;
    private final EventsApiClient eventsClient;
    private final StandsApiClient standsClient;
    private final SagaEventPublisher publisher;
    private final Clock clock;

    public CreateEventWithStandsOrchestrator(
            SagaRepository sagaRepository,
            EventsApiClient eventsClient,
            StandsApiClient standsClient,
            SagaEventPublisher publisher,
            Clock clock
    ) {
        this.sagaRepository = sagaRepository;
        this.eventsClient = eventsClient;
        this.standsClient = standsClient;
        this.publisher = publisher;
        this.clock = clock;
    }

    public record Request(
            String name,
            String place,
            LocalDate startDate,
            LocalDate endDate,
            String descriptionIt,
            String descriptionEn,
            List<UUID> standIds
    ) {}

    @Transactional
    public SagaInstanceEntity execute(Request req) {
        SagaInstanceEntity saga = new SagaInstanceEntity(
                UUID.randomUUID(), SAGA_TYPE, SagaStatus.STARTED, Instant.now(clock)
        );
        // Re-assign so we get back the managed instance (Spring Data uses merge()
        // when @Id is assigned manually, so the original object stays detached).
        saga = sagaRepository.save(saga);

        SagaStepEntity step1 = newStep(saga, "createEvent", 1);
        UUID eventId;
        try {
            EventsApiClient.CreatedEvent created = eventsClient.createEvent(toEventRequest(req));
            eventId = created.id();
            saga.setCreatedEventId(eventId);
            completeStep(step1, "Created event " + eventId);
            log.info("[saga {}] step1 createEvent OK -> {}", saga.getId(), eventId);
        } catch (Exception ex) {
            failStep(step1, ex.getMessage());
            saga.setStatus(SagaStatus.FAILED);
            saga.setMessage("createEvent failed: " + ex.getMessage());
            saga.setFinishedAt(Instant.now(clock));
            log.error("[saga {}] step1 createEvent FAILED: {}", saga.getId(), ex.getMessage());
            publishAfterCommit(SagaEvent.compensated(
                    saga.getId(), SAGA_TYPE, null,
                    "createEvent failed: " + ex.getMessage(), Instant.now(clock)
            ));
            return saga;
        }

        SagaStepEntity step2 = newStep(saga, "validateStands", 2);
        try {
            for (UUID standId : req.standIds()) {
                if (!standsClient.standExists(standId)) {
                    throw new IllegalStateException("Stand not found: " + standId);
                }
            }
            completeStep(step2, "Validated " + req.standIds().size() + " stands");
            log.info("[saga {}] step2 validateStands OK ({} stands)", saga.getId(), req.standIds().size());
        } catch (Exception ex) {
            failStep(step2, ex.getMessage());
            compensate(saga, eventId, ex.getMessage());
            publishAfterCommit(SagaEvent.compensated(
                    saga.getId(), SAGA_TYPE, eventId, ex.getMessage(), Instant.now(clock)
            ));
            return saga;
        }

        SagaStepEntity step3 = newStep(saga, "registerParticipations", 3);
        try {
            for (UUID standId : req.standIds()) {
                saga.addParticipation(new ParticipationEntity(UUID.randomUUID(), eventId, standId));
            }
            completeStep(step3, "Registered " + req.standIds().size() + " participations");
            log.info("[saga {}] step3 registerParticipations OK", saga.getId());
        } catch (Exception ex) {
            failStep(step3, ex.getMessage());
            compensate(saga, eventId, ex.getMessage());
            publishAfterCommit(SagaEvent.compensated(
                    saga.getId(), SAGA_TYPE, eventId, ex.getMessage(), Instant.now(clock)
            ));
            return saga;
        }

        saga.setStatus(SagaStatus.COMPLETED);
        saga.setFinishedAt(Instant.now(clock));
        log.info("[saga {}] COMPLETED", saga.getId());
        publishAfterCommit(SagaEvent.completed(
                saga.getId(), SAGA_TYPE, eventId, req.standIds(), Instant.now(clock)
        ));
        return saga;
    }

    private void publishAfterCommit(SagaEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publish(event);
                }
            });
        } else {
            // Fallback per chiamate fuori da una transazione (es. test)
            publisher.publish(event);
        }
    }

    private void compensate(SagaInstanceEntity saga, UUID eventIdToDelete, String rootCause) {
        log.warn("[saga {}] compensating: deleting event {}", saga.getId(), eventIdToDelete);
        saga.setStatus(SagaStatus.COMPENSATING);
        try {
            eventsClient.deleteEvent(eventIdToDelete);
            // Marca lo step createEvent come compensated
            saga.getSteps().stream()
                    .filter(s -> "createEvent".equals(s.getName()))
                    .findFirst()
                    .ifPresent(s -> s.setStatus(SagaStepStatus.COMPENSATED));
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setMessage("Compensated. Root cause: " + rootCause);
        } catch (Exception ex) {
            saga.setStatus(SagaStatus.FAILED);
            saga.setMessage("Compensation FAILED: " + ex.getMessage()
                    + " (root cause: " + rootCause + ")");
            log.error("[saga {}] compensation FAILED: {}", saga.getId(), ex.getMessage());
        }
        // Le participations gia' aggiunte vengono rollbackate dal Tx commit del Service
        // perche' siamo ancora dentro la stessa transazione locale.
        saga.getParticipations().clear();
        saga.setFinishedAt(Instant.now(clock));
    }

    private SagaStepEntity newStep(SagaInstanceEntity saga, String name, int order) {
        SagaStepEntity step = new SagaStepEntity(UUID.randomUUID(), name, order, SagaStepStatus.PENDING);
        step.setStartedAt(Instant.now(clock));
        saga.addStep(step);
        return step;
    }

    private void completeStep(SagaStepEntity step, String message) {
        step.setStatus(SagaStepStatus.COMPLETED);
        step.setMessage(message);
        step.setFinishedAt(Instant.now(clock));
    }

    private void failStep(SagaStepEntity step, String message) {
        step.setStatus(SagaStepStatus.FAILED);
        step.setMessage(message);
        step.setFinishedAt(Instant.now(clock));
    }

    private EventsApiClient.EventCreateRequest toEventRequest(Request req) {
        return new EventsApiClient.EventCreateRequest(
                req.name(),
                req.place(),
                req.startDate(),
                req.endDate(),
                req.descriptionIt(),
                req.descriptionEn()
        );
    }
}
