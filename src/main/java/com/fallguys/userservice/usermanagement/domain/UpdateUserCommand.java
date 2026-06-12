package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;

public record UpdateUserCommand(
        String keycloakId,
        String email,
        String displayName,
        String tenancyCode,
        String position,
        UserRole role
) {

    public UpdateUserCommand {
        keycloakId = required(keycloakId, "keycloakId");
        email = required(email, "email");
        displayName = required(displayName, "displayName");
        tenancyCode = required(tenancyCode, "tenancyCode");
        position = normalize(position);
        role = required(role, "role");
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
}
