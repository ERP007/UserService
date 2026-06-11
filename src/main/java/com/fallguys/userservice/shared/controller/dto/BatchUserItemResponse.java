package com.fallguys.userservice.shared.controller.dto;

import com.fallguys.userservice.shared.domain.query.BatchUser;

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
