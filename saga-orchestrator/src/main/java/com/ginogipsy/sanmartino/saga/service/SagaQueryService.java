package com.ginogipsy.sanmartino.saga.service;

import com.ginogipsy.sanmartino.saga.domain.SagaInstanceEntity;
import com.ginogipsy.sanmartino.saga.domain.SagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SagaQueryService {

    private final SagaRepository repository;

    public SagaQueryService(SagaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SagaInstanceEntity findById(UUID id) {
        SagaInstanceEntity saga = repository.findById(id).orElseThrow(() -> new SagaNotFoundException(id));
        // Materializza collections per la serializzazione fuori dalla sessione
        saga.getSteps().size();
        saga.getParticipations().size();
        return saga;
    }

    @Transactional(readOnly = true)
    public List<SagaInstanceEntity> findAll() {
        List<SagaInstanceEntity> sagas = repository.findAllByOrderByStartedAtDesc();
        sagas.forEach(s -> {
            s.getSteps().size();
            s.getParticipations().size();
        });
        return sagas;
    }
}
