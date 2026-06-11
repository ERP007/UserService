package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;

final class TemporaryPasswordPolicy {

    static final int MIN_LENGTH = 8;

    private TemporaryPasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || !hasLetter(password) || !hasDigit(password)) {
            throw new UserException(UserErrorCode.USER_TEMPORARY_PASSWORD_INVALID);
        }
    }

    private static boolean hasLetter(String password) {
        return password.chars().anyMatch(Character::isLetter);
    }

    private static boolean hasDigit(String password) {
        return password.chars().anyMatch(Character::isDigit);
    }
}
