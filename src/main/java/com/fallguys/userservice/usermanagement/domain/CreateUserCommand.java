package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;

public record CreateUserCommand(
        String employeeNumber,
        String email,
        String displayName,
        String tenancyCode,
        String position,
        UserRole role,
        UserTenancy tenancy,
        PasswordIssueMode passwordIssueMode,
        String initialPassword
) {

    public CreateUserCommand {
        employeeNumber = required(employeeNumber, "employeeNumber");
        email = required(email, "email");
        displayName = required(displayName, "displayName");
        tenancyCode = required(tenancyCode, "tenancyCode");
        position = normalize(position);
        role = required(role, "role");
        tenancy = required(tenancy, "tenancy");
        passwordIssueMode = required(passwordIssueMode, "passwordIssueMode");
        initialPassword = normalize(initialPassword);
        if (passwordIssueMode == PasswordIssueMode.MANUAL) {
            initialPassword = required(initialPassword, "initialPassword");
            TemporaryPasswordPolicy.validate(initialPassword);
        } else if (initialPassword != null) {
            throw new IllegalArgumentException("initialPassword must be null when passwordIssueMode is AUTO");
        }
    }

    private static String required(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        return normalized;
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
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
