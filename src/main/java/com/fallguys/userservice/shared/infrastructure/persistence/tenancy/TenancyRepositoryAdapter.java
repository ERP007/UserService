package com.fallguys.userservice.shared.infrastructure.persistence.tenancy;

import java.util.Optional;

import com.fallguys.userservice.shared.domain.model.Tenancy;
import com.fallguys.userservice.shared.domain.TenancyRepository;
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
