package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;

public record UpdateUserCommand(
        String keycloakId,
        String email,
        String displayName,
        String tenancyCode,
        String tenancyName,
        String position,
        UserRole role,
        UserTenancy tenancy
) {

    public UpdateUserCommand {
        keycloakId = required(keycloakId, "keycloakId");
        email = required(email, "email");
        displayName = required(displayName, "displayName");
        tenancyCode = required(tenancyCode, "tenancyCode");
        tenancyName = defaultToTenancyCode(tenancyName, tenancyCode);
        position = normalize(position);
        role = required(role, "role");
        tenancy = tenancy == null ? UserTenancy.fromRole(role) : tenancy;
    }

    private static String required(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return normalized;
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return value;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String defaultToTenancyCode(String tenancyName, String tenancyCode) {
        String normalized = normalize(tenancyName);
        return normalized == null ? tenancyCode : normalized;
    }
}
