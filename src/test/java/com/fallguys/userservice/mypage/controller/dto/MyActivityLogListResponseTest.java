package com.fallguys.userservice.mypage.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyActivityLogListResponseTest {

    @Test
    void serializesRecentActivityLogsForMyPage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        ActivityLog activityLog = ActivityLog.restore(
                2L,
                "7c3e0b76-44d0-4f53-9e65-200000000013",
                "ADMIN002",
                UserActionType.STOCK_ADJUSTED,
                Instant.parse("2026-05-20T05:22:00Z"),
                "엔진오일 필터",
                "HMC-EN-00214",
                "-12",
                "inventory-service",
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013",
                Instant.parse("2026-05-20T05:22:03Z")
        );

        String json = objectMapper.writeValueAsString(MyActivityLogListResponse.from(List.of(activityLog)));

        assertThat(json)
                .contains("\"content\":[")
                .contains("\"id\":2")
                .contains("\"employee_no\":\"ADMIN002\"")
                .contains("\"actionType\":\"STOCK_ADJUSTED\"")
                .contains("\"title\":\"엔진오일 필터\"")
                .contains("\"content\":\"HMC-EN-00214\"")
                .contains("\"status\":\"-12\"")
                .contains("\"occurredAt\":\"2026-05-20 14:22:00\"");
    }
}
