package com.fallguys.userservice.domain;

public record Tenancy(
        String tenancyCode,
        String name,
        TenancyType type
) {
}
