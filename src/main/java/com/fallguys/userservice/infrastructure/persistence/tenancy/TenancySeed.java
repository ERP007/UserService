package com.fallguys.userservice.infrastructure.persistence.tenancy;

import com.fallguys.userservice.domain.TenancyType;

public record TenancySeed(String tenancyCode, String name, TenancyType type) {

}
