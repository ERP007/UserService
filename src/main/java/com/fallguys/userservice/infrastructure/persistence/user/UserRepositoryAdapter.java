package com.fallguys.userservice.infrastructure.persistence.user;

import java.util.Optional;

import com.fallguys.userservice.domain.User;
import com.fallguys.userservice.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaDao userJpaDao;

    @Override
    public Optional<User> findByKeycloakId(String keycloakId) {
        return userJpaDao.findByKeycloakId(keycloakId).map(UserEntity::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity = user.getId() == null
                ? UserEntity.from(user)
                : userJpaDao.findById(user.getId())
                        .map(existing -> existing.update(user))
                        .orElseGet(() -> UserEntity.from(user));

        return userJpaDao.save(entity).toDomain();
    }
}
