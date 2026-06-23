package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEvent;
import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutboxUserAuthorityChangedEventPublisher implements UserAuthorityChangedEventPublisher {

    private static final String AGGREGATE_TYPE = "USER";

    private final OutboxEventJpaDao outboxEventJpaDao;
    private final UserAuthorityRabbitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(UserAuthorityChangedEvent event) {
        UserAuthorityChangedMessage message = UserAuthorityChangedMessage.from(event);
        outboxEventJpaDao.save(OutboxEventEntity.pending(
                message.eventId(),
                message.eventType(),
                AGGREGATE_TYPE,
                event.keycloakSub(),
                properties.getExchange(),
                properties.getRoutingKey(),
                toJson(message),
                Instant.parse(message.occurredAt())
        ));
    }

    private String toJson(UserAuthorityChangedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize user authority changed event", ex);
        }
    }
}
