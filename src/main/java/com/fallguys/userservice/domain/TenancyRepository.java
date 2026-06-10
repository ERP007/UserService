package com.fallguys.userservice.domain;

import java.util.Optional;

public interface TenancyRepository {

    Optional<Tenancy> findByCode(String tenancyCode);
}
