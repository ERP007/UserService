package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:activity-log-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ActivityLogJpaDaoTest {

    private static final String EVENT_ID = "7c3e0b76-44d0-4f53-9e65-200000000013";

    @Autowired
    private ActivityLogJpaDao activityLogJpaDao;

    @Test
    void savesActivityLogByEventId() {
        ActivityLog activityLog = ActivityLog.create(new CreateActivityLogCommand(
                EVENT_ID,
                "ADMIN002",
                UserActionType.STOCK_ADJUSTED,
                Instant.parse("2026-06-24T10:15:30Z"),
                "엔진오일 필터",
                "HMC-EN-00214",
                "-3",
                "inventory-service",
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013"
        ));

        ActivityLogEntity saved = activityLogJpaDao.saveAndFlush(ActivityLogEntity.from(activityLog));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getEmployeeNo()).isEqualTo("ADMIN002");
        assertThat(saved.getAction()).isEqualTo(UserActionType.STOCK_ADJUSTED);
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-06-24T10:15:30Z"));
        assertThat(saved.getTitle()).isEqualTo("엔진오일 필터");
        assertThat(saved.getContent()).isEqualTo("HMC-EN-00214");
        assertThat(saved.getStatus()).isEqualTo("-3");
        assertThat(saved.getProducer()).isEqualTo("inventory-service");
        assertThat(saved.getCorrelationId()).isEqualTo("INV-7c3e0b76-44d0-4f53-9e65-200000000013");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(activityLogJpaDao.existsByEventId(EVENT_ID)).isTrue();
    }
}
