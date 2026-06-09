package com.fallguys.userservice.controller.dto;

import com.fallguys.userservice.domain.User;

public record SuspendToggleResponse(
        String userId,
        String employeeNo,
        String email,
        String name,
        String status
) {

    public static SuspendToggleResponse from(User user) {
        return new SuspendToggleResponse(
                user.getKeycloakId(),
                user.getEmployeeNumber(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name()
        );
    }
}
