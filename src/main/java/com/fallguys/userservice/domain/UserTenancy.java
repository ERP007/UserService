package com.fallguys.userservice.domain;

import java.util.Locale;
import java.util.Optional;

public enum UserTenancy {
    ADMIN,
    HQ,
    BRANCH,
    WAREHOUSE;

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
