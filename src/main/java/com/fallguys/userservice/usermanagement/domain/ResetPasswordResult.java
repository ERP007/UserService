package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.User;

public record ResetPasswordResult(
        User user,
        String temporaryPassword
) {
}
