package com.fallguys.userservice.shared.domain.model;

public record UserIdentity(
        String keycloakId,
        String employeeNumber,
        String email,
        String displayName,
        String tenancyCode,
        String tenancyName,
        String position,
        UserRole role,
        UserTenancy tenancy,
        boolean enabled
) {
}
