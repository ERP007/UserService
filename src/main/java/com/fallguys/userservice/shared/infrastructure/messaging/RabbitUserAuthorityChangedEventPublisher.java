package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEvent;
import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitUserAuthorityChangedEventPublisher implements UserAuthorityChangedEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final UserAuthorityRabbitProperties properties;

    @Override
    public void publish(UserAuthorityChangedEvent event) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                UserAuthorityChangedMessage.from(event)
        );
    }
}
