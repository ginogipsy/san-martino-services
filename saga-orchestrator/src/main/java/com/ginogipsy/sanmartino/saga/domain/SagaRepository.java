package com.ginogipsy.sanmartino.saga.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SagaRepository extends JpaRepository<SagaInstanceEntity, UUID> {

    List<SagaInstanceEntity> findAllByOrderByStartedAtDesc();
}
