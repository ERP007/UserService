package com.fallguys.userservice.shared.controller.dto;

public record SessionContentResponse(
        String tenancyCode,
        String tenancyName,
        String userRole,
        String position,
        String employeeNo,
        String name
) {
}
