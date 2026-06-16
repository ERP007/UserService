package com.fallguys.userservice.usermanagement.controller.dto;

import com.fallguys.userservice.shared.domain.model.User;

public record UserResponse(
        Long id,
        String keycloakId,
        String employeeNumber,
        String email,
        String displayName,
        String tenancyCode,
        String tenancyName,
        String position,
        String role,
        String tenancy,
        String status
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getEmployeeNumber(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTenancyCode(),
                user.getTenancyName(),
                user.getPosition(),
                user.getRole().name(),
                user.getTenancy() == null ? null : user.getTenancy().name(),
                user.getStatus().name()
        );
    }
}
