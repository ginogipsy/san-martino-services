package com.ginogipsy.sanmartino.stands.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFavoriteStandRepository extends JpaRepository<UserFavoriteStandEntity, UUID> {

    List<UserFavoriteStandEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);

    Optional<UserFavoriteStandEntity> findByUserIdAndStandId(String userId, UUID standId);

    void deleteByUserIdAndStandId(String userId, UUID standId);
}
