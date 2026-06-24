package com.fallguys.userservice.shared.domain.activity;

public interface ActivityLogRepository {

    boolean existsByEventId(String eventId);

    ActivityLog save(ActivityLog activityLog);
}
