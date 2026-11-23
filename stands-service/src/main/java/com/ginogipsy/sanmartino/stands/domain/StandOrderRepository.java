package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StandOrderRepository extends JpaRepository<StandOrderEntity, UUID> {

    List<StandOrderEntity> findAllByStandIdAndStatusInOrderByOrderNumberAsc(
            UUID standId,
            Collection<StandOrderStatus> statuses
    );

    List<StandOrderEntity> findTop10ByStandIdAndStatusOrderByOrderNumberDesc(
            UUID standId,
            StandOrderStatus status
    );

    List<StandOrderEntity> findAllByStatusIn(Collection<StandOrderStatus> statuses);
}
