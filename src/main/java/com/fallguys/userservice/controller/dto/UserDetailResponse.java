package com.fallguys.userservice.controller.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fallguys.userservice.domain.UserDetail;

public record UserDetailResponse(
        String userId,
        String employeeNo,
        String name,
        String email,
        String tenancyCode,
        String tenancyName,
        String role,
        String position,
        String status,
        LocalDate joinedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastLoginAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastChangedPassAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt
) {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    public static UserDetailResponse from(UserDetail user) {
        return new UserDetailResponse(
                user.userId(),
                user.employeeNo(),
                user.name(),
                user.email(),
                user.tenancyCode(),
                user.tenancyName(),
                user.role() == null ? null : user.role().name(),
                user.position(),
                user.status() == null ? null : user.status().name(),
                user.joinedAt(),
                toLocalDateTime(user.lastLoginAt()),
                toLocalDateTime(user.lastChangedPassAt()),
                user.createdAt()
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, RESPONSE_ZONE);
    }
}
