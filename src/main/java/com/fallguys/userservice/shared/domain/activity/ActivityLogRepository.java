package com.fallguys.userservice.shared.domain.activity;

import java.util.List;

public interface ActivityLogRepository {

    boolean saveIfAbsent(ActivityLog activityLog);

    List<ActivityLog> findRecentByEmployeeNo(String employeeNo, int limit);
}
