package com.fallguys.userservice.domain;

public record CreateUserResult(
        User user,
        String temporaryPassword
) {
}
