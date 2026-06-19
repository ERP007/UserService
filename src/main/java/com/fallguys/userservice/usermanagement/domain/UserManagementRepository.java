package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import java.util.List;
import java.util.Optional;

public interface UserManagementRepository {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findById(Long id);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    Optional<User> findByKeycloakIdForUpdate(String keycloakId);

    Optional<UserDetail> findDetailByKeycloakId(String keycloakId);

    List<User> findAll();

    boolean existsByEmployeeNumber(String employeeNumber);

    UserListPage findUsers(UserSearchQuery query);

    long countActiveAdminsForUpdate();

    User save(User user);

    void delete(User user);
}
