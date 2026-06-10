package com.fallguys.userservice.domain;

public record ResetPasswordResult(
        User user,
        String temporaryPassword
) {
}
