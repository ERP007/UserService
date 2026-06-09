package com.fallguys.userservice.controller.dto;

import com.fallguys.userservice.domain.CreateUserResult;

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
