package com.fallguys.userservice.infrastructure.persistence.tenancy;

import java.util.Optional;

import com.fallguys.userservice.domain.Tenancy;
import com.fallguys.userservice.domain.TenancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenancyRepositoryAdapter implements TenancyRepository {

    private final TenancyJpaDao tenancyJpaDao;

    @Override
    public Optional<Tenancy> findByCode(String tenancyCode) {
        return tenancyJpaDao.findById(tenancyCode).map(TenancyEntity::toDomain);
    }
}
