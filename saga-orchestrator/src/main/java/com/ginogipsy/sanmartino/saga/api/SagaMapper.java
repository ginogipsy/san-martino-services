package com.ginogipsy.sanmartino.saga.api;

import com.ginogipsy.sanmartino.saga.api.generated.model.Participation;
import com.ginogipsy.sanmartino.saga.api.generated.model.SagaInstance;
import com.ginogipsy.sanmartino.saga.api.generated.model.SagaStatus;
import com.ginogipsy.sanmartino.saga.api.generated.model.SagaStep;
import com.ginogipsy.sanmartino.saga.api.generated.model.SagaStepStatus;
import com.ginogipsy.sanmartino.saga.domain.ParticipationEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaInstanceEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaStepEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class SagaMapper {

    public SagaInstance toApi(SagaInstanceEntity entity) {
        List<SagaStep> steps = entity.getSteps().stream().map(this::toStep).toList();
        List<Participation> participations = entity.getParticipations().stream().map(this::toParticipation).toList();

        SagaInstance dto = new SagaInstance(
                entity.getId(),
                entity.getType(),
                SagaStatus.valueOf(entity.getStatus().name()),
                steps,
                participations
        );
        dto.setCreatedEventId(entity.getCreatedEventId());
        dto.setMessage(entity.getMessage());
        if (entity.getStartedAt() != null) {
            dto.setStartedAt(OffsetDateTime.ofInstant(entity.getStartedAt(), ZoneOffset.UTC));
        }
        if (entity.getFinishedAt() != null) {
            dto.setFinishedAt(OffsetDateTime.ofInstant(entity.getFinishedAt(), ZoneOffset.UTC));
        }
        return dto;
    }

    private SagaStep toStep(SagaStepEntity step) {
        SagaStep dto = new SagaStep(
                step.getName(),
                step.getStepOrder(),
                SagaStepStatus.valueOf(step.getStatus().name())
        );
        if (step.getStartedAt() != null) {
            dto.setStartedAt(OffsetDateTime.ofInstant(step.getStartedAt(), ZoneOffset.UTC));
        }
        if (step.getFinishedAt() != null) {
            dto.setFinishedAt(OffsetDateTime.ofInstant(step.getFinishedAt(), ZoneOffset.UTC));
        }
        dto.setMessage(step.getMessage());
        return dto;
    }

    private Participation toParticipation(ParticipationEntity entity) {
        return new Participation(entity.getEventId(), entity.getStandId());
    }
}
