package com.fallguys.userservice.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.ActivityLogRepository;
import com.fallguys.userservice.shared.domain.activity.ActivityLogService;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    private static final String EVENT_ID = "7c3e0b76-44d0-4f53-9e65-200000000013";
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-24T10:15:30Z");

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Test
    void recordsActivityLogWhenEventIsNotDuplicated() {
        ActivityLogService service = new ActivityLogService(activityLogRepository);
        CreateActivityLogCommand command = command();
        when(activityLogRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(command);

        ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(logCaptor.capture());
        ActivityLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getEventId()).isEqualTo(EVENT_ID);
        assertThat(savedLog.getEmployeeNo()).isEqualTo("ADMIN002");
        assertThat(savedLog.getAction()).isEqualTo(UserActionType.STOCK_ADJUSTED);
        assertThat(savedLog.getActionLabel()).isEqualTo("재고 조정");
        assertThat(savedLog.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(savedLog.getTitle()).isEqualTo("엔진오일 필터");
        assertThat(savedLog.getContent()).isEqualTo("HMC-EN-00214");
        assertThat(savedLog.getStatus()).isEqualTo("-3");
        assertThat(savedLog.getProducer()).isEqualTo("inventory-service");
        assertThat(savedLog.getCorrelationId()).isEqualTo("INV-7c3e0b76-44d0-4f53-9e65-200000000013");
    }

    @Test
    void skipsDuplicatedEventAsSuccess() {
        ActivityLogService service = new ActivityLogService(activityLogRepository);
        when(activityLogRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        service.record(command());

        verify(activityLogRepository, never()).save(any(ActivityLog.class));
    }

    @Test
    void findsRecentActivityLogsByEmployeeNo() {
        ActivityLogService service = new ActivityLogService(activityLogRepository);
        ActivityLog activityLog = ActivityLog.create(command());
        when(activityLogRepository.findRecentByEmployeeNo("ADMIN002", 5)).thenReturn(List.of(activityLog));

        List<ActivityLog> activityLogs = service.findRecentByEmployeeNo("ADMIN002", 5);

        assertThat(activityLogs).containsExactly(activityLog);
    }

    private CreateActivityLogCommand command() {
        return new CreateActivityLogCommand(
                EVENT_ID,
                "ADMIN002",
                UserActionType.STOCK_ADJUSTED,
                OCCURRED_AT,
                "엔진오일 필터",
                "HMC-EN-00214",
                "-3",
                "inventory-service",
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013"
        );
    }
}
