package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.ActivityLogRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ActivityLogRepositoryAdapter implements ActivityLogRepository {

    private final ActivityLogJpaDao activityLogJpaDao;
    private final TransactionTemplate saveTransactionTemplate;

    public ActivityLogRepositoryAdapter(
            ActivityLogJpaDao activityLogJpaDao,
            PlatformTransactionManager transactionManager
    ) {
        this.activityLogJpaDao = activityLogJpaDao;
        this.saveTransactionTemplate = new TransactionTemplate(transactionManager);
        this.saveTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean saveIfAbsent(ActivityLog activityLog) {
        try {
            saveTransactionTemplate.executeWithoutResult(status ->
                    activityLogJpaDao.saveAndFlush(ActivityLogEntity.from(activityLog))
            );
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (activityLogJpaDao.existsByEventId(activityLog.getEventId())) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public List<ActivityLog> findRecentByEmployeeNo(String employeeNo, int limit) {
        return activityLogJpaDao.findByEmployeeNoOrderByOccurredAtDescIdDesc(
                        employeeNo,
                        PageRequest.of(0, limit)
                ).stream()
                .map(ActivityLogEntity::toDomain)
                .toList();
    }

}
