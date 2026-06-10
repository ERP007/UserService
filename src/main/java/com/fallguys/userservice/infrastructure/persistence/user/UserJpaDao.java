package com.fallguys.userservice.infrastructure.persistence.user;

import java.util.List;
import java.util.Optional;

import com.fallguys.userservice.domain.BatchUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaDao extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);

    @EntityGraph(attributePaths = "tenancyEntity")
    @Query("select u from UserEntity u where u.keycloakId = :keycloakId")
    Optional<UserEntity> findDetailByKeycloakId(@Param("keycloakId") String keycloakId);

    @Override
    @EntityGraph(attributePaths = "tenancyEntity")
    Page<UserEntity> findAll(Specification<UserEntity> specification, Pageable pageable);

    @Query("""
            select new com.fallguys.userservice.domain.BatchUser(
                u.employeeNumber,
                u.name,
                u.position
            )
            from UserEntity u
            where lower(u.employeeNumber) in :employeeNumbers
            """)
    List<BatchUser> findBatchUsersByEmployeeNumbers(@Param("employeeNumbers") List<String> employeeNumbers);
}
