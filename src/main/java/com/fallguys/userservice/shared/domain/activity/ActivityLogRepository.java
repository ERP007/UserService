package com.fallguys.userservice.shared.domain.activity;

import java.util.List;

public interface ActivityLogRepository {

    boolean existsByEventId(String eventId);

    List<ActivityLog> findRecentByEmployeeNo(String employeeNo, int limit);

    ActivityLog save(ActivityLog activityLog);
}
