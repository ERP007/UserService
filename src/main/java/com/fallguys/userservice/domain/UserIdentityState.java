package com.fallguys.userservice.domain;

public record UserIdentityState(
        boolean enabled,
        boolean passwordUpdateRequired
) {
}
