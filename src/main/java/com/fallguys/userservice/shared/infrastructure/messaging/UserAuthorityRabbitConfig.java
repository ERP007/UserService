package com.fallguys.userservice.shared.infrastructure.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserAuthorityRabbitProperties.class)
public class UserAuthorityRabbitConfig {

    @Bean
    TopicExchange userAuthorityExchange(UserAuthorityRabbitProperties properties) {
        return ExchangeBuilder.topicExchange(properties.getExchange())
                .durable(true)
                .build();
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
