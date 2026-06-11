package com.fallguys.userservice.shared.infrastructure.persistence.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenancyJpaDao extends JpaRepository<TenancyEntity, String> {
}
