package com.fallguys.userservice.mypage.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record MyActivityLogResponse(
        Long id,

        @JsonProperty("employee_no")
        String employeeNo,

        String actionType,
        String title,
        String content,
        String status,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime occurredAt
) {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    public static MyActivityLogResponse from(ActivityLog activityLog) {
        return new MyActivityLogResponse(
                activityLog.getId(),
                activityLog.getEmployeeNo(),
                activityLog.getAction().name(),
                activityLog.getTitle(),
                activityLog.getContent(),
                activityLog.getStatus(),
                toLocalDateTime(activityLog.getOccurredAt())
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return LocalDateTime.ofInstant(instant, RESPONSE_ZONE);
    }
}
