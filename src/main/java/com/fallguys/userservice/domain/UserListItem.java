package com.fallguys.userservice.domain;

import java.time.LocalDate;

public record UserListItem(
        String userId,
        String employeeNo,
        String name,
        String email,
        String department,
        UserRole role,
        UserStatus status,
        LocalDate joinedAt
) {
}
