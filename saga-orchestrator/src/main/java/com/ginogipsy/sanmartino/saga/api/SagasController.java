package com.ginogipsy.sanmartino.saga.api;

import com.ginogipsy.sanmartino.saga.api.generated.SagasApi;
import com.ginogipsy.sanmartino.saga.api.generated.model.CreateEventWithStandsRequest;
import com.ginogipsy.sanmartino.saga.api.generated.model.SagaInstance;
import com.ginogipsy.sanmartino.saga.domain.SagaInstanceEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaStatus;
import com.ginogipsy.sanmartino.saga.service.CreateEventWithStandsOrchestrator;
import com.ginogipsy.sanmartino.saga.service.SagaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SagasController implements SagasApi {

    private final CreateEventWithStandsOrchestrator orchestrator;
    private final SagaQueryService queryService;
    private final SagaMapper mapper;

    public SagasController(
            CreateEventWithStandsOrchestrator orchestrator,
            SagaQueryService queryService,
            SagaMapper mapper
    ) {
        this.orchestrator = orchestrator;
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SagaInstance> startCreateEventWithStands(CreateEventWithStandsRequest body) {
        CreateEventWithStandsOrchestrator.Request req = new CreateEventWithStandsOrchestrator.Request(
                body.getEvent().getName(),
                body.getEvent().getPlace(),
                body.getEvent().getStartDate(),
                body.getEvent().getEndDate(),
                body.getEvent().getDescription().getIt(),
                body.getEvent().getDescription().getEn(),
                body.getStandIds()
        );
        SagaInstanceEntity result = orchestrator.execute(req);
        SagaInstance dto = mapper.toApi(result);
        HttpStatus status = result.getStatus() == SagaStatus.COMPLETED
                ? HttpStatus.OK
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(dto);
    }

    @Override
    public ResponseEntity<SagaInstance> getSaga(UUID id) {
        return ResponseEntity.ok(mapper.toApi(queryService.findById(id)));
    }

    @Override
    public ResponseEntity<List<SagaInstance>> listSagas() {
        return ResponseEntity.ok(queryService.findAll().stream().map(mapper::toApi).toList());
    }
}
