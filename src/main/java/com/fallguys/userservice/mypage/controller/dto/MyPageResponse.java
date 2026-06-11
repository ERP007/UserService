package com.fallguys.userservice.mypage.controller.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fallguys.userservice.shared.domain.query.UserDetail;

public record MyPageResponse(
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
        LocalDateTime lastChangedPassAt
) {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    public static MyPageResponse from(UserDetail user) {
        return new MyPageResponse(
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
                toLocalDateTime(user.lastChangedPassAt())
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, RESPONSE_ZONE);
    }
}
