package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StandRepository extends JpaRepository<StandEntity, UUID> {

    List<StandEntity> findAllByOrderByNumberAsc();
}
