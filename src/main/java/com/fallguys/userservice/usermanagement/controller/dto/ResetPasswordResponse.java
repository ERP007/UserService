package com.fallguys.userservice.usermanagement.controller.dto;

import com.fallguys.userservice.usermanagement.domain.ResetPasswordResult;

public record ResetPasswordResponse(
        UserResponse user,
        String temporaryPassword
) {

    public static ResetPasswordResponse from(ResetPasswordResult result) {
        return new ResetPasswordResponse(
                UserResponse.from(result.user()),
                result.temporaryPassword()
        );
    }
}
