package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.shared.domain.activity.ActivityLogService;
import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityOccurredListener {

    private final ActivityLogService activityLogService;

    @RabbitListener(queues = UserActivityRabbitConfig.USER_ACTIVITY_LOG_QUEUE)
    public void handle(UserActivityOccurredMessage message) {
        if (message == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        CreateActivityLogCommand command = message.toCommand();
        activityLogService.record(command);
        log.info("Recorded user activity log. eventId={}, employeeNo={}, action={}",
                command.eventId(),
                command.employeeNo(),
                command.action()
        );
    }
}
