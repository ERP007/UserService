package com.fallguys.userservice.shared.domain.model;

public record UserIdentityState(
        boolean enabled,
        boolean passwordUpdateRequired
) {
}
