package com.fallguys.userservice.shared.domain.query;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserDetail(
        String userId,
        String employeeNo,
        String name,
        String email,
        String tenancyCode,
        String tenancyName,
        UserRole role,
        String position,
        UserStatus status,
        LocalDate joinedAt,
        Instant lastLoginAt,
        Instant lastChangedPassAt,
        LocalDateTime createdAt
) {
}
