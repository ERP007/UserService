package com.fallguys.userservice.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

class UserAuthorityRabbitConfigTest {

    private final UserAuthorityRabbitConfig config = new UserAuthorityRabbitConfig();

    @Test
    void createsUserAuthorityExchangeForPublishing() {
        UserAuthorityRabbitProperties properties = new UserAuthorityRabbitProperties();

        TopicExchange exchange = config.userAuthorityExchange(properties);

        assertThat(exchange.getName()).isEqualTo("erp.events");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(properties.getRoutingKey()).isEqualTo("user.authority.changed.gateway");
    }

    @Test
    void usesJacksonJsonMessageConverterForRabbitMessages() {
        MessageConverter messageConverter = config.rabbitMessageConverter();

        assertThat(messageConverter).isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
