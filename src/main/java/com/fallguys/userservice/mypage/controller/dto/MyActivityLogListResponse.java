package com.fallguys.userservice.mypage.controller.dto;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import java.util.List;

public record MyActivityLogListResponse(
        List<MyActivityLogResponse> content
) {

    public static MyActivityLogListResponse from(List<ActivityLog> activityLogs) {
        return new MyActivityLogListResponse(
                activityLogs.stream()
                        .map(MyActivityLogResponse::from)
                        .toList()
        );
    }
}
