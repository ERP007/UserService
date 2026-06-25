package com.fallguys.userservice.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class UserActivityRabbitConfigTest {

    private final UserActivityRabbitConfig config = new UserActivityRabbitConfig();

    @Test
    void declaresUserActivityQueueAndBinding() {
        TopicExchange exchange = config.userActivityEventsExchange();
        Queue queue = config.userActivityLogQueue();
        Binding binding = config.userActivityLogBinding(queue, exchange);

        assertThat(exchange.getName()).isEqualTo("erp.events");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo("user.activity-log.q");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "erp.events")
                .containsEntry("x-dead-letter-routing-key", "user.activity.occurred.dlq");
        assertThat(binding.getRoutingKey()).isEqualTo("user.activity.occurred");
    }

    @Test
    void declaresUserActivityDeadLetterQueueAndBinding() {
        TopicExchange exchange = config.userActivityEventsExchange();
        Queue dlq = config.userActivityLogDlq();
        Binding binding = config.userActivityLogDlqBinding(dlq, exchange);

        assertThat(dlq.getName()).isEqualTo("user.activity-log.dlq");
        assertThat(dlq.isDurable()).isTrue();
        assertThat(binding.getRoutingKey()).isEqualTo("user.activity.occurred.dlq");
    }
}
