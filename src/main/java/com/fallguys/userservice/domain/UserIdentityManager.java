package com.fallguys.userservice.domain;

import java.time.Instant;
import java.util.Optional;

public interface UserIdentityManager {

    Optional<UserIdentity> findById(String keycloakId);

    Optional<Instant> findPasswordChangedAt(String keycloakId);

    UserIdentity create(CreateUserCommand command);

    void resetPassword(String keycloakId, String temporaryPassword);

    UserIdentityState toggleEnabled(String keycloakId);

    void delete(String keycloakId);
}
