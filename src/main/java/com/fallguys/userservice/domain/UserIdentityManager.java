package com.fallguys.userservice.domain;

import java.util.Optional;

public interface UserIdentityManager {

    Optional<UserIdentity> findById(String keycloakId);

    UserIdentity create(CreateUserCommand command);

    void delete(String keycloakId);
}
