package com.fallguys.userservice.shared.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration(proxyBeanMethods = false)
public class UserActivityRabbitConfig {

    public static final String ERP_EVENTS_EXCHANGE = "erp.events";
    public static final String USER_ACTIVITY_LOG_QUEUE = "user.activity-log.q";
    public static final String USER_ACTIVITY_LOG_DLQ = "user.activity-log.dlq";
    public static final String USER_ACTIVITY_OCCURRED_ROUTING_KEY = "user.activity.occurred";
    public static final String USER_ACTIVITY_OCCURRED_DLQ_ROUTING_KEY = "user.activity.occurred.dlq";
    public static final int USER_ACTIVITY_OCCURRED_EVENT_VERSION = 1;

    @Bean
    TopicExchange userActivityEventsExchange() {
        return ExchangeBuilder.topicExchange(ERP_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    Queue userActivityLogQueue() {
        return QueueBuilder.durable(USER_ACTIVITY_LOG_QUEUE)
                .deadLetterExchange(ERP_EVENTS_EXCHANGE)
                .deadLetterRoutingKey(USER_ACTIVITY_OCCURRED_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue userActivityLogDlq() {
        return QueueBuilder.durable(USER_ACTIVITY_LOG_DLQ).build();
    }

    @Bean
    Binding userActivityLogBinding(
            @Qualifier("userActivityLogQueue") Queue userActivityLogQueue,
            @Qualifier("userActivityEventsExchange") TopicExchange userActivityEventsExchange
    ) {
        return BindingBuilder
                .bind(userActivityLogQueue)
                .to(userActivityEventsExchange)
                .with(USER_ACTIVITY_OCCURRED_ROUTING_KEY);
    }

    @Bean
    Binding userActivityLogDlqBinding(
            @Qualifier("userActivityLogDlq") Queue userActivityLogDlq,
            @Qualifier("userActivityEventsExchange") TopicExchange userActivityEventsExchange
    ) {
        return BindingBuilder
                .bind(userActivityLogDlq)
                .to(userActivityEventsExchange)
                .with(USER_ACTIVITY_OCCURRED_DLQ_ROUTING_KEY);
    }
}
