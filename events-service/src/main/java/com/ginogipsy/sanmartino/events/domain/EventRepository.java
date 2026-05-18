package com.ginogipsy.sanmartino.events.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    List<EventEntity> findAllByEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate today);

    List<EventEntity> findAllByEndDateLessThanOrderByStartDateDesc(LocalDate today);
}
