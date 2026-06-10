package com.fallguys.userservice.domain;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<UserDetail> findDetailByKeycloakId(String keycloakId);

    UserListPage findUsers(UserSearchQuery query);

    List<BatchUser> findBatchUsersByEmployeeNumbers(List<String> employeeNumbers);

    User save(User user);
}
