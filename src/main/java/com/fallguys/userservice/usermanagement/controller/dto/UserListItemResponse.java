package com.fallguys.userservice.usermanagement.controller.dto;

import java.time.LocalDate;

import com.fallguys.userservice.usermanagement.domain.UserListItem;

public record UserListItemResponse(
        String userId,
        String employeeNo,
        String name,
        String email,
        String department,
        String role,
        String status,
        LocalDate joinedAt
) {

    public static UserListItemResponse from(UserListItem user) {
        return new UserListItemResponse(
                user.userId(),
                user.employeeNo(),
                user.name(),
                user.email(),
                user.department(),
                user.role() == null ? null : user.role().name(),
                user.status() == null ? null : user.status().name(),
                user.joinedAt()
        );
    }
}
