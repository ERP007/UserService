package com.fallguys.userservice.shared.infrastructure.persistence.tenancy;

import com.fallguys.userservice.shared.domain.model.TenancyType;

public record TenancySeed(String tenancyCode, String name, TenancyType type) {

}
