package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import java.util.Optional;

public interface UserManagementRepository {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByKeycloakIdForUpdate(String keycloakId);

    Optional<UserDetail> findDetailByKeycloakId(String keycloakId);

    UserListPage findUsers(UserSearchQuery query);

    User save(User user);
}
