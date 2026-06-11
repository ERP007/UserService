package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
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
