package com.fallguys.userservice.shared.domain.command;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;

public record UpdateUserIdentityCommand(
        String keycloakId,
        String email,
        String displayName,
        String tenancyCode,
        String position,
        UserRole role,
        UserTenancy tenancy
) {

    public UpdateUserIdentityCommand {
        keycloakId = required(keycloakId);
        email = required(email);
        displayName = required(displayName);
        tenancyCode = required(tenancyCode);
        position = normalize(position);
        role = required(role);
        tenancy = required(tenancy);
    }

    private static String required(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return normalized;
    }

    private static <T> T required(T value) {
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
}
