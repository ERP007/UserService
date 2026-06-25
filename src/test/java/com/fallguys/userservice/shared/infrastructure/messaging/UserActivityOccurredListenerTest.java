package com.fallguys.userservice.shared.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.fallguys.userservice.shared.domain.activity.ActivityLogService;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserActivityOccurredListenerTest {

    @Test
    void recordsActivityLogFromRabbitMessage() {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        UserActivityOccurredListener listener = new UserActivityOccurredListener(activityLogService);

        listener.handle(new UserActivityOccurredMessage(
                "7c3e0b76-44d0-4f53-9e65-200000000013",
                UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_ROUTING_KEY,
                UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_EVENT_VERSION,
                "inventory-service",
                Instant.parse("2026-06-24T10:15:30Z"),
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013",
                new UserActivityOccurredMessage.UserActivityOccurredPayload(
                        "ADMIN002",
                        UserActionType.STOCK_ADJUSTED,
                        Instant.parse("2026-06-24T10:15:30Z"),
                        "엔진오일 필터",
                        "HMC-EN-00214",
                        "-3"
                )
        ));

        verify(activityLogService).record(argThat(command ->
                command.eventId().equals("7c3e0b76-44d0-4f53-9e65-200000000013")
                        && command.employeeNo().equals("ADMIN002")
                        && command.action() == UserActionType.STOCK_ADJUSTED
        ));
    }
}
