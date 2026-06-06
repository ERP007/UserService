package com.fallguys.userservice.domain;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByKeycloakId(String keycloakId);

    User save(User user);
}
