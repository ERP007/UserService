package com.fallguys.userservice.shared.domain.model;

import java.util.Locale;
import java.util.Optional;

public enum UserRole {
    ADMIN,
    HQ_STAFF,
    HQ_MANAGER,
    BRANCH_STAFF,
    BRANCH_MANAGER,
    WAREHOUSE_STAFF,
    WAREHOUSE_MANAGER;

    public static Optional<UserRole> fromClaim(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
