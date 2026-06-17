package com.fallguys.userservice.shared.domain.model;

import java.util.Locale;
import java.util.Optional;

public enum UserTenancy {
    ADMIN,
    HQ,
    BRANCH,
    WAREHOUSE;

    public static UserTenancy fromRole(UserRole role) {
        return switch (role) {
            case ADMIN -> ADMIN;
            case HQ_STAFF, HQ_MANAGER -> HQ;
            case BRANCH_STAFF, BRANCH_MANAGER -> BRANCH;
            case WAREHOUSE_STAFF, WAREHOUSE_MANAGER -> WAREHOUSE;
        };
    }

    public static Optional<UserTenancy> fromClaim(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UserTenancy.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
