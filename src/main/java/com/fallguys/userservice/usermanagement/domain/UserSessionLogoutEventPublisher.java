package com.fallguys.userservice.usermanagement.domain;

public interface UserSessionLogoutEventPublisher {

    void publish(UserSessionLogoutEvent event);
}
