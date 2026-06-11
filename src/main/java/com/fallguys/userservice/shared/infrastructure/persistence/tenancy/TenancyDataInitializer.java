package com.fallguys.userservice.shared.infrastructure.persistence.tenancy;

import java.util.List;

import com.fallguys.userservice.shared.domain.model.TenancyType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@RequiredArgsConstructor
public class TenancyDataInitializer implements ApplicationRunner {

    private final TenancyJpaDao tenancyJpaDao;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedData().forEach(this::upsert);
    }

    private void upsert(TenancySeed seed) {
        tenancyJpaDao.findById(seed.tenancyCode())
                .ifPresentOrElse(
                        tenancy -> tenancy.updateSeedData(seed.name(), seed.type()),
                        () -> tenancyJpaDao.save(TenancyEntity.create(seed.tenancyCode(), seed.name(), seed.type()))
                );
    }

    private List<TenancySeed> seedData() {
        return List.of(
                new TenancySeed("ADMIN", "관리자", TenancyType.ADMIN),
                new TenancySeed("HQ", "본사", TenancyType.HQ),
                new TenancySeed("WH-HQ-001", "본사 중앙창고", TenancyType.HQ),
                new TenancySeed("WH-HQ-002", "판교 물류센터", TenancyType.HQ),
                new TenancySeed("WH-BR-001", "강남 1지점", TenancyType.BRANCH),
                new TenancySeed("WH-BR-002", "분당 1지점", TenancyType.BRANCH),
                new TenancySeed("WH-BR-003", "부산 1지점", TenancyType.BRANCH),
                new TenancySeed("WH-BR-004", "대구 1지점", TenancyType.BRANCH)
        );
    }
}
