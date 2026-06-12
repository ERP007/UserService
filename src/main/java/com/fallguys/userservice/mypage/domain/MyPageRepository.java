package com.fallguys.userservice.mypage.domain;

import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import java.util.Optional;

public interface MyPageRepository {

    Optional<UserDetail> findDetailByKeycloakId(String keycloakId);

    User save(User user);
}
