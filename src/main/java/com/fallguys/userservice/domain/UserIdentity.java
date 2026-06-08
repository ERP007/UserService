package com.fallguys.userservice.domain;

public record UserIdentity(
        String keycloakId,
        String employeeNumber,
        String email,
        String displayName,
        String tenancyCode,
        String position,
        UserRole role,
        UserTenancy tenancy,
        boolean enabled
) {
}
