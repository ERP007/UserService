package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.JwtClaims;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.domain.command.CreateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.command.TemporaryPasswordPolicy;
import com.fallguys.userservice.shared.domain.command.UpdateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.exception.UserAlreadyExistsException;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.exception.UserIdentityException;
import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserIdentity;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserManagementService {

    private static final int KEYCLOAK_SYNC_MAX_ATTEMPTS = 3;
    private static final double KEYCLOAK_DELETE_SAFETY_MIN_RATIO = 0.5D;

    private final UserManagementRepository userRepository;
    private final UserIdentityManager userIdentityManager;

    /**
     * 관리자 전용 사용자 목록을 조회한다.
     *
     * 흐름:
     * 1) JWT Claim의 tenancy_code와 user_role이 모두 ADMIN인지 확인한다.
정     * 2) 조회 조건(keyword, role, tenancyCode, status)과 정렬·페이지 조건을 repository에 전달한다.
     * 3) repository가 검색 결과와 전체 페이지 정보를 함께 반환한다.
     *
     * 트랜잭션: 읽기 전용. 사용자 목록 조회만 수행하며 Keycloak 전체 동기화는 수행하지 않는다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 조회 중단.
     * - 필수 권한 Claim 누락 또는 미지원 값: UserException(403 매핑), 조회 중단.
     */
    @Transactional(readOnly = true)
    public UserListPage findUsers(Jwt jwt, UserSearchQuery query) {
        JwtClaims.requireAdmin(jwt);
        return userRepository.findUsers(query);
    }

    /**
     * 관리자 요청으로 Keycloak 전체 사용자 정보를 로컬 사용자 DB와 동기화한다.
     *
     * 흐름:
     * 1) JWT Claim의 tenancy_code와 user_role이 모두 ADMIN인지 확인한다.
     * 2) Keycloak 전체 ERP 사용자 정보를 조회한다.
     * 3) Keycloak 사용자별로 로컬 사용자 DB에 upsert한다.
     * 4) Keycloak에 더 이상 없는 로컬 사용자를 삭제한다.
     *
     * 트랜잭션: 쓰기. 전체 동기화 중 예외가 발생하면 로컬 DB 변경은 롤백된다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 동기화 중단.
     * - Keycloak 전체 사용자 조회 실패: UserIdentityException(502 매핑), 동기화 중단.
     */
    @Transactional
    public void synchronizeUsersFromKeycloak(Jwt jwt) {
        JwtClaims.requireAdmin(jwt);
        synchronizeUsersFromKeycloak();
    }

    /**
     * 관리자 전용 사용자 상세 정보를 조회한다.
     *
     * 흐름:
     * 1) JWT Claim의 tenancy_code와 user_role이 모두 ADMIN인지 확인한다.
     * 2) keycloakId로 로컬 사용자와 소속 정보를 함께 조회한다.
     * 3) 상세 화면에 필요한 사용자 기본 정보와 로그인·비밀번호 변경 시각을 반환한다.
     *
     * 트랜잭션: 읽기 전용. 사용자 상세 정보만 조회하며 상태를 변경하지 않는다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 조회 중단.
     * - 사용자 없음: UserException(404 매핑), 조회 중단.
     */
    @Transactional(readOnly = true)
    public UserDetail findUserDetail(Jwt jwt, String keycloakId) {
        JwtClaims.requireAdmin(jwt);

        return userRepository.findDetailByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 관리자 요청으로 사용자 상세 화면에서 수정 가능한 프로필 정보를 변경한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 로컬 사용자 row를 쓰기 잠금으로 조회해 같은 사용자에 대한 관리자 변경을 직렬화한다.
     * 3) Keycloak 사용자 현재 프로필을 보상 복구용으로 조회한다.
     * 4) Keycloak 사용자 프로필을 먼저 수정한다(외부 호출).
     * 5) 로컬 사용자 프로필을 갱신해 저장한다.
     * 6) 상세 조회 응답을 반환한다.
     *
     * 트랜잭션: 쓰기. 사용자 row 잠금은 커밋/롤백 시 해제된다. Keycloak 수정 실패 시 로컬 DB는 변경하지 않는다.
     * 로컬 저장 실패 또는 커밋 롤백 시 Keycloak 프로필은 수정 전 값으로 복구를 시도한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 수정 중단.
     * - 사용자 없음: UserException(404 매핑), 수정 중단.
     * - Keycloak 조회/수정 실패: UserIdentityException, 로컬 DB 변경 전 중단.
     * - 로컬 저장 실패: RuntimeException, 트랜잭션 롤백 및 Keycloak 프로필 복구 시도.
     */
    @Transactional
    public UserDetail updateUser(Jwt jwt, UpdateUserCommand command) {
        JwtClaims.requireAdmin(jwt);

        User user = userRepository.findByKeycloakIdForUpdate(command.keycloakId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        UpdateUserIdentityCommand previousIdentityCommand = previousIdentityCommand(command.keycloakId());
        UpdateUserIdentityCommand identityCommand = new UpdateUserIdentityCommand(
                command.keycloakId(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.tenancyName(),
                command.position(),
                command.role(),
                command.tenancy()
        );
        userIdentityManager.update(identityCommand);
        AtomicBoolean identityRestored = new AtomicBoolean(false);
        registerIdentityRestoreRollbackCleanup(previousIdentityCommand, identityRestored);

        try {
            user.updateProfile(
                    command.email(),
                    command.displayName(),
                    command.tenancyCode(),
                    command.tenancyName(),
                    command.position(),
                    command.role(),
                    command.tenancy()
            );
            userRepository.save(user);

            return userRepository.findDetailByKeycloakId(command.keycloakId())
                    .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        } catch (RuntimeException ex) {
            restoreUpdatedIdentity(previousIdentityCommand, identityRestored, ex);
            throw ex;
        }
    }

    /**
     * 관리자 요청으로 Keycloak 사용자와 로컬 사용자를 함께 생성한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 서버에서 임시 비밀번호를 생성한다.
     * 3) UserIdentityManager로 Keycloak 사용자를 생성하고 실제 Keycloak ID를 돌려받는다(외부 호출).
     * 4) 이후 트랜잭션이 롤백되면 생성된 Keycloak 사용자를 삭제하도록 보상 훅을 등록한다.
     * 5) 생성된 Keycloak ID를 로컬 사용자와 매핑해 PENDING 상태로 저장한다.
     *
     * 트랜잭션: 쓰기. Keycloak 생성 실패 시 로컬 저장은 수행하지 않는다.
     * 로컬 저장 실패 또는 커밋 롤백 시 생성된 Keycloak 사용자는 삭제를 시도한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 생성 중단.
     * - Keycloak 사용자 중복 또는 생성 실패: BusinessException 계열, 로컬 저장 전 중단.
     * - 로컬 저장 실패: RuntimeException, 트랜잭션 롤백 및 Keycloak 사용자 삭제 시도. 삭제 실패는 suppressed로 보존한다.
     * - 커밋 시점 롤백: afterCompletion에서 Keycloak 사용자 삭제 시도. 삭제 실패는 로그로 남긴다.
     */
    @Transactional
    public CreateUserResult createUser(Jwt jwt, CreateUserCommand command) {
        JwtClaims.requireAdmin(jwt);
        rejectDuplicateEmployeeNumber(command.employeeNumber());

        String initialPassword = issueInitialPassword(command);
        CreateUserIdentityCommand identityCommand = new CreateUserIdentityCommand(
                null,
                command.employeeNumber(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.tenancyName(),
                command.position(),
                command.role(),
                command.tenancy(),
                initialPassword
        );
        UserIdentity identity = userIdentityManager.create(identityCommand);
        AtomicBoolean identityDeleted = new AtomicBoolean(false);
        registerIdentityRollbackCleanup(identity.keycloakId(), identityDeleted);
        User user = User.createPending(
                identity.keycloakId(),
                identity.employeeNumber(),
                identity.email(),
                identity.displayName(),
                identity.tenancyCode(),
                identity.tenancyName(),
                identity.position(),
                identity.role(),
                identity.tenancy()
        );

        try {
            User savedUser = userRepository.save(user);
            String responsePassword = command.passwordIssueMode() == PasswordIssueMode.AUTO ? initialPassword : null;
            return new CreateUserResult(savedUser, responsePassword);
        } catch (RuntimeException ex) {
            deleteCreatedIdentity(identity.keycloakId(), identityDeleted, ex);
            throw ex;
        }
    }

    /**
     * 관리자 요청으로 사용자의 Keycloak 비밀번호를 임시 비밀번호로 초기화한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 로컬 사용자 row를 쓰기 잠금으로 조회해 같은 사용자에 대한 관리자 변경을 직렬화한다.
     * 3) 서버에서 임시 비밀번호를 생성한다.
     * 4) 로컬 사용자 상태를 PENDING으로 변경해 저장한다.
     * 5) 트랜잭션 커밋 후 Keycloak에 temporary credential로 설정한다(외부 호출).
     *
     * 트랜잭션: 쓰기. 사용자 row 잠금은 커밋/롤백 시 해제되며, 로컬 저장 커밋이 성공한 뒤 Keycloak을 동기화한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 초기화 중단.
     * - 로컬 사용자 없음: UserException(404 매핑), 초기화 중단.
     * - Keycloak 초기화 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public ResetPasswordResult resetPassword(Jwt jwt, String keycloakId) {
        JwtClaims.requireAdmin(jwt);

        User user = userRepository.findByKeycloakIdForUpdate(keycloakId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        String temporaryPassword = issueTemporaryPassword();

        user.markPasswordResetRequired();
        User savedUser = userRepository.save(user);
        runAfterCommit("Keycloak 임시 비밀번호 설정", () -> userIdentityManager.resetPassword(keycloakId, temporaryPassword));

        return new ResetPasswordResult(savedUser, temporaryPassword);
    }

    /**
     * 관리자 요청으로 사용자 정지 여부를 명시적으로 변경한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 정지 요청이면 ACTIVE ADMIN row들을 동일한 순서로 잠가 마지막 관리자 정지 race를 방지한다.
     * 3) 로컬 사용자 row를 쓰기 잠금으로 조회해 같은 사용자에 대한 동시 상태 변경을 직렬화한다.
     * 4) suspended 요청값으로 Keycloak enabled 목표값을 계산한다.
     * 5) 정지 해제 요청이면 Keycloak required action을 조회해 PENDING/ACTIVE 복귀 상태를 결정한다(외부 조회).
     * 6) 목표 상태를 로컬 사용자에 반영해 저장한다.
     * 7) 트랜잭션 커밋 후 Keycloak enabled 값을 목표값으로 변경한다(외부 호출).
     *
     * 트랜잭션: 쓰기. 사용자 row 잠금은 커밋/롤백 시 해제되며, 로컬 저장 커밋이 성공한 뒤 Keycloak을 동기화한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 상태 변경 중단.
     * - 로컬 사용자 없음: UserException(404 매핑), 상태 변경 중단.
     * - 마지막 활성 관리자 정지 요청: UserException(400 매핑), 상태 변경 중단.
     * - Keycloak 상태 변경 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public User updateSuspension(Jwt jwt, String keycloakId, boolean suspended) {
        JwtClaims.requireAdmin(jwt);

        long activeAdminCount = suspended ? userRepository.countActiveAdminsForUpdate() : Long.MAX_VALUE;
        User user = userRepository.findByKeycloakIdForUpdate(keycloakId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        rejectLastActiveAdminSuspension(user, suspended, activeAdminCount);

        boolean targetEnabled = !suspended;
        UserIdentityState targetState = suspensionTargetState(keycloakId, targetEnabled);

        user.applyIdentityState(targetState);
        User savedUser = userRepository.save(user);
        runAfterCommit("Keycloak enabled 상태 변경", () -> userIdentityManager.updateEnabled(keycloakId, targetState.enabled()));
        return savedUser;
    }

    private void rejectLastActiveAdminSuspension(User user, boolean suspended, long activeAdminCount) {
        if (!suspended || user.getRole() != UserRole.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        if (activeAdminCount <= 1) {
            throw new UserException(UserErrorCode.USER_LAST_ADMIN_SUSPENSION_NOT_ALLOWED);
        }
    }

    private UserIdentityState suspensionTargetState(String keycloakId, boolean targetEnabled) {
        if (!targetEnabled) {
            return new UserIdentityState(false, false);
        }

        UserIdentityState currentState = userIdentityManager.findState(keycloakId);
        return new UserIdentityState(true, currentState.passwordUpdateRequired());
    }

    private void synchronizeUsersFromKeycloak() {
        List<UserIdentity> identities = userIdentityManager.findAll();
        identities.forEach(this::synchronizeUserFromIdentity);
        deleteUsersMissingFromKeycloak(identities);
    }

    private void synchronizeUserFromIdentity(UserIdentity identity) {
        User user = userRepository.findByKeycloakId(identity.keycloakId())
                .or(() -> userRepository.findByEmployeeNumber(identity.employeeNumber()))
                .orElseGet(() -> User.createFromIdentity(identity));
        user.syncIdentityProfile(identity);
        userRepository.save(user);
    }

    private void deleteUsersMissingFromKeycloak(List<UserIdentity> identities) {
        Set<String> keycloakIds = identities.stream()
                .map(UserIdentity::keycloakId)
                .filter(this::hasText)
                .collect(Collectors.toSet());

        if (keycloakIds.isEmpty()) {
            log.warn("Keycloak 사용자 동기화 삭제를 건너뜁니다. Keycloak 사용자 ID 목록이 비어 있습니다.");
            return;
        }

        List<User> localUsers = userRepository.findAll();
        if (keycloakIds.size() < localUsers.size() * KEYCLOAK_DELETE_SAFETY_MIN_RATIO) {
            log.warn("Keycloak 사용자 동기화 삭제를 건너뜁니다. Keycloak 사용자 수가 로컬 사용자 수보다 현저히 적습니다. "
                            + "keycloakCount={}, localCount={}",
                    keycloakIds.size(),
                    localUsers.size());
            return;
        }

        localUsers.stream()
                .filter(user -> missingFromKeycloak(user, keycloakIds))
                .forEach(userRepository::delete);
    }

    private boolean missingFromKeycloak(User user, Set<String> keycloakIds) {
        return hasText(user.getKeycloakId()) && !keycloakIds.contains(user.getKeycloakId());
    }

    private String issueInitialPassword(CreateUserCommand command) {
        if (command.passwordIssueMode() == PasswordIssueMode.AUTO) {
            return issueTemporaryPassword();
        }

        return command.initialPassword();
    }

    private String issueTemporaryPassword() {
        String generatedPassword = TemporaryPasswordGenerator.generate();
        TemporaryPasswordPolicy.validate(generatedPassword);
        return generatedPassword;
    }

    private void runAfterCommit(String operation, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runWithRetry(operation, action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runWithRetry(operation, action);
            }
        });
    }

    private void registerIdentityRollbackCleanup(String keycloakId, AtomicBoolean identityDeleted) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteCreatedIdentity(keycloakId, identityDeleted, null);
                }
            }
        });
    }

    private void registerIdentityRestoreRollbackCleanup(
            UpdateUserIdentityCommand previousIdentityCommand,
            AtomicBoolean identityRestored
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    restoreUpdatedIdentity(previousIdentityCommand, identityRestored, null);
                }
            }
        });
    }

    private void deleteCreatedIdentity(String keycloakId, AtomicBoolean identityDeleted, RuntimeException sourceFailure) {
        if (!identityDeleted.compareAndSet(false, true)) {
            return;
        }

        try {
            userIdentityManager.delete(keycloakId);
        } catch (RuntimeException deleteEx) {
            if (sourceFailure != null) {
                sourceFailure.addSuppressed(deleteEx);
                return;
            }

            log.warn("Keycloak 생성 사용자 롤백 보상 삭제 실패. keycloakId={}", keycloakId, deleteEx);
        }
    }

    private void restoreUpdatedIdentity(
            UpdateUserIdentityCommand previousIdentityCommand,
            AtomicBoolean identityRestored,
            RuntimeException sourceFailure
    ) {
        if (!identityRestored.compareAndSet(false, true)) {
            return;
        }

        try {
            userIdentityManager.update(previousIdentityCommand);
        } catch (RuntimeException restoreEx) {
            if (sourceFailure != null) {
                sourceFailure.addSuppressed(restoreEx);
                return;
            }

            log.warn("Keycloak 사용자 정보 롤백 보상 복구 실패. keycloakId={}",
                    previousIdentityCommand.keycloakId(),
                    restoreEx);
        }
    }

    private UpdateUserIdentityCommand previousIdentityCommand(String keycloakId) {
        return userIdentityManager.findById(keycloakId)
                .map(this::toUpdateIdentityCommand)
                .orElseThrow(() -> new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED));
    }

    private UpdateUserIdentityCommand toUpdateIdentityCommand(UserIdentity identity) {
        return new UpdateUserIdentityCommand(
                identity.keycloakId(),
                identity.email(),
                identity.displayName(),
                identity.tenancyCode(),
                identity.tenancyName(),
                identity.position(),
                identity.role(),
                identity.tenancy()
        );
    }

    private void rejectDuplicateEmployeeNumber(String employeeNumber) {
        if (userRepository.existsByEmployeeNumber(employeeNumber)
                || userIdentityManager.existsByEmployeeNumber(employeeNumber)) {
            throw new UserAlreadyExistsException();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void runWithRetry(String operation, Runnable action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= KEYCLOAK_SYNC_MAX_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("{} 실패. attempt={}/{}", operation, attempt, KEYCLOAK_SYNC_MAX_ATTEMPTS, ex);
            }
        }

        throw lastFailure;
    }
}
