package com.fallguys.userservice.shared.domain.command;

import com.fallguys.userservice.shared.domain.activity.UserActionType;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import java.time.Instant;

public record CreateActivityLogCommand(
        String eventId,
        String employeeNo,
        UserActionType action,
        Instant occurredAt,
        String title,
        String content,
        String status,
        String producer,
        String correlationId
) {

    public CreateActivityLogCommand {
        eventId = requireText(eventId, 36);
        employeeNo = requireText(employeeNo, 50);
        if (action == null || occurredAt == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }
        title = requireText(title, 100);
        content = normalizeText(content, 100);
        status = normalizeText(status, 50);
        producer = requireText(producer, 50);
        correlationId = normalizeText(correlationId, 100);
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return trimmed;
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return trimmed;
    }
}
