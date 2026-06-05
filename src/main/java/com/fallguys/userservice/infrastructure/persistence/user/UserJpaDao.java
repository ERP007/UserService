package com.fallguys.userservice.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaDao extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);
}
