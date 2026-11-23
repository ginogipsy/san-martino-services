package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandStaffPermissionRepository extends JpaRepository<StandStaffPermissionEntity, UUID> {

    List<StandStaffPermissionEntity> findAllByStandIdOrderByUsernameAscPermissionAsc(UUID standId);

    List<StandStaffPermissionEntity> findAllByUsernameIgnoreCase(String username);

    Optional<StandStaffPermissionEntity> findByStandIdAndUsernameIgnoreCaseAndPermission(
            UUID standId,
            String username,
            StandStaffRole permission
    );
}
