package com.fallguys.userservice.usermanagement.controller.dto;

import com.fallguys.userservice.usermanagement.domain.CreateUserResult;

public record CreateUserResponse(
        UserResponse user,
        String temporaryPassword
) {

    public static CreateUserResponse from(CreateUserResult result) {
        return new CreateUserResponse(
                UserResponse.from(result.user()),
                result.temporaryPassword()
        );
    }
}
