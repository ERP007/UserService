package com.fallguys.userservice.usermanagement.domain;

public interface UserAuthorityChangedEventPublisher {

    void publish(UserAuthorityChangedEvent event);
}
