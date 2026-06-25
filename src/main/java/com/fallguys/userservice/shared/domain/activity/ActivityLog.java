package com.fallguys.userservice.shared.domain.activity;

import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ActivityLog {

    private final Long id;
    private final String eventId;
    private final String employeeNo;
    private final UserActionType action;
    private final Instant occurredAt;
    private final String title;
    private final String content;
    private final String status;
    private final String producer;
    private final String correlationId;
    private final Instant createdAt;

    public static ActivityLog create(CreateActivityLogCommand command) {
        return new ActivityLog(
                null,
                command.eventId(),
                command.employeeNo(),
                command.action(),
                command.occurredAt(),
                command.title(),
                command.content(),
                command.status(),
                command.producer(),
                command.correlationId(),
                null
        );
    }

    public static ActivityLog restore(
            Long id,
            String eventId,
            String employeeNo,
            UserActionType action,
            Instant occurredAt,
            String title,
            String content,
            String status,
            String producer,
            String correlationId,
            Instant createdAt
    ) {
        return new ActivityLog(
                id,
                eventId,
                employeeNo,
                action,
                occurredAt,
                title,
                content,
                status,
                producer,
                correlationId,
                createdAt
        );
    }

    public String getActionLabel() {
        return action.getLabel();
    }
}
