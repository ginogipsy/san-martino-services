package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandKeypadSlotRepository extends JpaRepository<StandKeypadSlotEntity, UUID> {

    List<StandKeypadSlotEntity> findAllByStandIdOrderBySlotNumberAsc(UUID standId);

    Optional<StandKeypadSlotEntity> findByStandIdAndSlotNumber(UUID standId, int slotNumber);
}
