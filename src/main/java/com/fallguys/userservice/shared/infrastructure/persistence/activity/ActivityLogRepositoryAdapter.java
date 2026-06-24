package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActivityLogRepositoryAdapter implements ActivityLogRepository {

    private final ActivityLogJpaDao activityLogJpaDao;

    @Override
    public boolean existsByEventId(String eventId) {
        return activityLogJpaDao.existsByEventId(eventId);
    }

    @Override
    public ActivityLog save(ActivityLog activityLog) {
        return activityLogJpaDao.save(ActivityLogEntity.from(activityLog))
                .toDomain();
    }
}
