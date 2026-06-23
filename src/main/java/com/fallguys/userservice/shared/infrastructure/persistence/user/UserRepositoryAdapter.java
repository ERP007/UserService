package com.fallguys.userservice.shared.infrastructure.persistence.user;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fallguys.userservice.mypage.domain.MyPageRepository;
import com.fallguys.userservice.shared.domain.InternalUserRepository;
import com.fallguys.userservice.shared.domain.SessionRepository;
import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.shared.domain.query.BatchUser;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import com.fallguys.userservice.usermanagement.domain.UserListItem;
import com.fallguys.userservice.usermanagement.domain.UserListPage;
import com.fallguys.userservice.usermanagement.domain.UserManagementRepository;
import com.fallguys.userservice.usermanagement.domain.UserSearchQuery;
import com.fallguys.userservice.usermanagement.domain.UserSortBy;
import com.fallguys.userservice.usermanagement.domain.UserSortDirection;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements SessionRepository, MyPageRepository, UserManagementRepository, InternalUserRepository {

    private final UserJpaDao userJpaDao;

    @Override
    public Optional<User> findByKeycloakId(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            return Optional.empty();
        }

        return userJpaDao.findByKeycloakId(keycloakId).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return userJpaDao.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmployeeNumber(String employeeNumber) {
        if (!StringUtils.hasText(employeeNumber)) {
            return Optional.empty();
        }

        return userJpaDao.findFirstByEmployeeNumberIgnoreCaseOrderByIdAsc(employeeNumber).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByKeycloakIdForUpdate(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            return Optional.empty();
        }

        return userJpaDao.findByKeycloakIdForUpdate(keycloakId).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmployeeNumber(String employeeNumber) {
        return userJpaDao.countByEmployeeNumberIgnoreCase(employeeNumber) > 0;
    }

    @Override
    public Optional<UserDetail> findDetailByKeycloakId(String keycloakId) {
        return userJpaDao.findDetailByKeycloakId(keycloakId)
                .map(this::toDetail);
    }

    @Override
    public List<User> findAll() {
        return userJpaDao.findAll().stream()
                .map(UserEntity::toDomain)
                .toList();
    }

    @Override
    public UserListPage findUsers(UserSearchQuery query) {
        PageRequest pageRequest = PageRequest.of(query.page() - 1, query.size(), sort(query));
        Page<UserEntity> page = userJpaDao.findAll(specification(query), pageRequest);

        return new UserListPage(
                page.getContent().stream()
                        .map(this::toListItem)
                        .toList(),
                query.page(),
                query.size(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasPrevious(),
                page.hasNext()
        );
    }

    @Override
    public List<BatchUser> findBatchUsersByEmployeeNumbers(List<String> employeeNumbers) {
        return userJpaDao.findBatchUsersByEmployeeNumbers(employeeNumbers);
    }

    @Override
    public long countActiveAdminsForUpdate() {
        return userJpaDao.findByRoleAndStatusForUpdate(UserRole.ADMIN, UserStatus.ACTIVE).size();
    }

    @Override
    public User save(User user) {
        UserEntity entity = user.getId() == null
                ? UserEntity.from(user)
                : userJpaDao.findById(user.getId())
                  .map(existing -> existing.update(user))
                  .orElseThrow(() -> new IllegalStateException(
                          "User entity with id=" + user.getId() + " not found in database"));

        return userJpaDao.save(entity).toDomain();
    }

    @Override
    public void delete(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        userJpaDao.deleteByIdIfExists(user.getId());
    }

    private Specification<UserEntity> specification(UserSearchQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.keyword())) {
                String keyword = "%" + query.keyword().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), keyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("employeeNumber")), keyword)
                ));
            }

            if (query.role() != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), query.role()));
            }

            if (StringUtils.hasText(query.tenancyCode())) {
                predicates.add(criteriaBuilder.equal(root.get("tenancyCode"), query.tenancyCode()));
            }

            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort sort(UserSearchQuery query) {
        Sort.Direction direction = query.sortDirection() == UserSortDirection.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direction, sortProperty(query.sortBy()));
    }

    private String sortProperty(UserSortBy sortBy) {
        return switch (sortBy) {
            case EMPLOYEE_NO -> "employeeNumber";
            case NAME -> "name";
            case JOINED_AT -> "createdAt";
        };
    }

    private UserListItem toListItem(UserEntity entity) {
        return new UserListItem(
                entity.getKeycloakId(),
                entity.getEmployeeNumber(),
                entity.getName(),
                entity.getEmail(),
                displayTenancyName(entity),
                entity.getRole(),
                entity.getStatus(),
                joinedAt(entity)
        );
    }

    private UserDetail toDetail(UserEntity entity) {
        return new UserDetail(
                entity.getKeycloakId(),
                entity.getEmployeeNumber(),
                entity.getName(),
                entity.getEmail(),
                entity.getTenancyCode(),
                displayTenancyName(entity),
                entity.getRole(),
                entity.getPosition(),
                entity.getStatus(),
                joinedAt(entity),
                entity.getLastLoginAt(),
                entity.getPasswordChangedAt(),
                entity.getCreatedAt()
        );
    }

    private String displayTenancyName(UserEntity entity) {
        if (StringUtils.hasText(entity.getTenancyName())) {
            return entity.getTenancyName();
        }

        return entity.getTenancyCode();
    }

    private LocalDate joinedAt(UserEntity entity) {
        if (entity.getCreatedAt() == null) {
            return null;
        }

        return entity.getCreatedAt().toLocalDate();
    }
}
