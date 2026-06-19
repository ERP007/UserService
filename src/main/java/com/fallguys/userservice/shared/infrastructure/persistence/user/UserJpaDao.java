package com.fallguys.userservice.shared.infrastructure.persistence.user;

import java.util.List;
import java.util.Optional;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.shared.domain.query.BatchUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaDao extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByKeycloakId(String keycloakId);

    Optional<UserEntity> findFirstByEmployeeNumberIgnoreCaseOrderByIdAsc(String employeeNumber);

    long countByEmployeeNumberIgnoreCase(String employeeNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.keycloakId = :keycloakId")
    Optional<UserEntity> findByKeycloakIdForUpdate(@Param("keycloakId") String keycloakId);

    @Query("select u from UserEntity u where u.keycloakId = :keycloakId")
    Optional<UserEntity> findDetailByKeycloakId(@Param("keycloakId") String keycloakId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserEntity u where u.id = :id")
    int deleteByIdIfExists(@Param("id") Long id);

    @Override
    Page<UserEntity> findAll(Specification<UserEntity> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.role = :role and u.status = :status order by u.id")
    List<UserEntity> findByRoleAndStatusForUpdate(
            @Param("role") UserRole role,
            @Param("status") UserStatus status
    );

    @Query("""
            select new com.fallguys.userservice.shared.domain.query.BatchUser(
                u.employeeNumber,
                u.name,
                u.position
            )
            from UserEntity u
            where lower(u.employeeNumber) in :employeeNumbers
            """)
    List<BatchUser> findBatchUsersByEmployeeNumbers(@Param("employeeNumbers") List<String> employeeNumbers);
}
