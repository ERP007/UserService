package com.fallguys.userservice.usermanagement.domain;

public record UserSessionLogoutEvent(
        String keycloakSub
) {

    public UserSessionLogoutEvent {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            throw new IllegalArgumentException("keycloakSub must not be blank");
        }
    }
}
