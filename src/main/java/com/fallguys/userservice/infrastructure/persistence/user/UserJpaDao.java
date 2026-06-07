package com.fallguys.userservice.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserJpaDao extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);

    @Override
    @EntityGraph(attributePaths = "tenancyEntity")
    Page<UserEntity> findAll(Specification<UserEntity> specification, Pageable pageable);
}
