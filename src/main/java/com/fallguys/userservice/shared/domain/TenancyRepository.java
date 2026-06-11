package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.Tenancy;
import java.util.Optional;

public interface TenancyRepository {

    Optional<Tenancy> findByCode(String tenancyCode);
}
