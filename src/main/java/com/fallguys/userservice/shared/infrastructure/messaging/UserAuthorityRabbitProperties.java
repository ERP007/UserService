package com.fallguys.userservice.shared.infrastructure.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "user-authority.rabbitmq")
public class UserAuthorityRabbitProperties {

    private String exchange = "erp.events";

    private String routingKey = "user.authority.changed.gateway";
}
