package com.fallguys.userservice.usermanagement.controller.dto;

import com.fallguys.userservice.shared.domain.model.User;

public record SuspensionResponse(
        String userId,
        String employeeNo,
        String email,
        String name,
        String status
) {

    public static SuspensionResponse from(User user) {
        return new SuspensionResponse(
                user.getKeycloakId(),
                user.getEmployeeNumber(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name()
        );
    }
}
