package com.fallguys.userservice.controller.dto;

import com.fallguys.userservice.domain.BatchUser;

public record BatchUserItemResponse(
        String employeeNumber,
        String name,
        String position
) {

    public static BatchUserItemResponse from(BatchUser user) {
        return new BatchUserItemResponse(
                user.employeeNumber(),
                user.name(),
                user.position()
        );
    }
}
