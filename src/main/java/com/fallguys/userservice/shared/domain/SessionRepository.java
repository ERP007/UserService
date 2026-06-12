package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.User;
import java.util.Optional;

public interface SessionRepository {

    Optional<User> findByKeycloakId(String keycloakId);

    User save(User user);
}
