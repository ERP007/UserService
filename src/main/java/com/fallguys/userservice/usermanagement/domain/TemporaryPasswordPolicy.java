package com.fallguys.userservice.usermanagement.domain;

final class TemporaryPasswordPolicy {

    static final int MIN_LENGTH = 8;

    private TemporaryPasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || !hasLetter(password) || !hasDigit(password)) {
            throw new IllegalArgumentException(
                    "initialPassword must be at least 8 characters and contain letters and digits"
            );
        }
    }

    private static boolean hasLetter(String password) {
        return password.chars().anyMatch(Character::isLetter);
    }

    private static boolean hasDigit(String password) {
        return password.chars().anyMatch(Character::isDigit);
    }
}
