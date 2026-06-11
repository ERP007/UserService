package com.fallguys.userservice.shared.domain.command;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;

public final class TemporaryPasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private TemporaryPasswordPolicy() {
    }

    public static void validate(String password) {
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
