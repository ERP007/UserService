package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserActivityOccurredMessage(
        String eventId,
        String eventType,
        Integer eventVersion,
        String producer,
        Instant occurredAt,
        String correlationId,
        UserActivityOccurredPayload payload
) {

    public CreateActivityLogCommand toCommand() {
        if (!UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_ROUTING_KEY.equals(eventType)
                || eventVersion == null
                || eventVersion != UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_EVENT_VERSION
                || payload == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        Instant activityOccurredAt = payload.occurredAt() == null ? occurredAt : payload.occurredAt();
        return new CreateActivityLogCommand(
                eventId,
                payload.employeeNo(),
                payload.action(),
                activityOccurredAt,
                payload.title(),
                payload.content(),
                payload.status(),
                producer,
                correlationId
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserActivityOccurredPayload(
            String employeeNo,
            UserActionType action,
            Instant occurredAt,
            String title,
            String content,
            String status
    ) {
    }
}
