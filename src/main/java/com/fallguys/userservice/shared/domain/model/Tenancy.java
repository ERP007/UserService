package com.fallguys.userservice.shared.domain.model;

public record Tenancy(
        String tenancyCode,
        String name,
        TenancyType type
) {
}
