package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.usermanagement.domain.UserSessionLogoutEvent;
import com.fallguys.userservice.usermanagement.domain.UserSessionLogoutEventPublisher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxUserSessionLogoutEventPublisher implements UserSessionLogoutEventPublisher {

    private static final String AGGREGATE_TYPE = "USER";

    private final OutboxEventJpaDao outboxEventJpaDao;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(UserSessionLogoutEvent event) {
        UserSessionLogoutMessage message = UserSessionLogoutMessage.from(event);
        outboxEventJpaDao.save(OutboxEventEntity.pending(
                message.eventId(),
                message.eventType(),
                AGGREGATE_TYPE,
                event.keycloakSub(),
                "internal",
                message.eventType(),
                toJson(message),
                Instant.parse(message.occurredAt())
        ));
    }

    private String toJson(UserSessionLogoutMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize user session logout event", ex);
        }
    }
}
