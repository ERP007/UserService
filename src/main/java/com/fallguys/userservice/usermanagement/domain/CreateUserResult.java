package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.User;

public record CreateUserResult(
        User user,
        String temporaryPassword
) {
}
