package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.UserIdentity;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.command.CreateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.command.UpdateUserIdentityCommand;
import java.time.Instant;
import java.util.Optional;

public interface UserIdentityManager {

    Optional<UserIdentity> findById(String keycloakId);

    Optional<Instant> findPasswordChangedAt(String keycloakId);

    UserIdentity create(CreateUserIdentityCommand command);

    void update(UpdateUserIdentityCommand command);

    void resetPassword(String keycloakId, String temporaryPassword);

    UserIdentityState findState(String keycloakId);

    void updateEnabled(String keycloakId, boolean enabled);

    void delete(String keycloakId);
}
