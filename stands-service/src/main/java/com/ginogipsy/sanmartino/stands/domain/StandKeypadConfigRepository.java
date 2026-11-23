package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StandKeypadConfigRepository extends JpaRepository<StandKeypadConfigEntity, UUID> {
}
