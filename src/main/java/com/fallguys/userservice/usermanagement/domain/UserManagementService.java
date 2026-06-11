package com.fallguys.userservice.usermanagement.domain;

import com.fallguys.userservice.shared.domain.JwtClaims;
import com.fallguys.userservice.shared.domain.TenancyRepository;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.domain.command.CreateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.command.TemporaryPasswordPolicy;
import com.fallguys.userservice.shared.domain.command.UpdateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.Tenancy;
import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserIdentity;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.shared.domain.query.UserDetail;
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

    private final UserManagementRepository userRepository;
    private final TenancyRepository tenancyRepository;
    private final UserIdentityManager userIdentityManager;

    /**
     * 관리자 전용 사용자 목록을 조회한다.
     *
     * 흐름:
     * 1) JWT Claim의 tenancy_code, tenancy_type, user_role이 모두 ADMIN인지 확인한다.
     * 2) 조회 조건(keyword, role, tenancyCode, status)과 정렬·페이지 조건을 repository에 전달한다.
     * 3) repository가 검색 결과와 전체 페이지 정보를 함께 반환한다.
     *
     * 트랜잭션: 읽기 전용. 사용자 목록과 페이지 메타데이터만 조회하며 상태를 변경하지 않는다.
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
     * 관리자 전용 사용자 상세 정보를 조회한다.
     *
     * 흐름:
     * 1) JWT Claim의 tenancy_code, tenancy_type, user_role이 모두 ADMIN인지 확인한다.
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
     * 2) 로컬 사용자와 변경 대상 소속 코드를 검증하고, 소속 타입을 조회한다.
     * 3) 로컬 사용자 프로필을 갱신해 저장한다.
     * 4) 트랜잭션 커밋 후 UserIdentityManager로 Keycloak 사용자 claim 원본을 수정한다(외부 호출).
     * 5) 상세 조회 응답을 반환한다.
     *
     * 트랜잭션: 쓰기. 로컬 저장 커밋이 성공한 뒤 Keycloak을 동기화한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 수정 중단.
     * - 사용자 없음: UserException(404 매핑), 수정 중단.
     * - 소속 코드 없음: UserException(400 매핑), 수정 중단.
     * - Keycloak 수정 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public UserDetail updateUser(Jwt jwt, UpdateUserCommand command) {
        JwtClaims.requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(command.keycloakId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        UserTenancy tenancy = resolveTenancy(command.tenancyCode());

        user.updateProfile(
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                tenancy
        );
        userRepository.save(user);
        UpdateUserIdentityCommand identityCommand = new UpdateUserIdentityCommand(
                command.keycloakId(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                tenancy
        );
        runAfterCommit("Keycloak 사용자 정보 수정", () -> userIdentityManager.update(identityCommand));

        return userRepository.findDetailByKeycloakId(command.keycloakId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 관리자 요청으로 Keycloak 사용자와 로컬 사용자를 함께 생성한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) tenancy_code가 가리키는 소속 타입과 요청 tenancy 값이 일치하는지 확인한다.
     * 3) UserIdentityManager로 Keycloak 사용자를 생성하고 Representation 기반 값을 돌려받는다(외부 호출).
     * 4) 생성된 Keycloak ID를 로컬 사용자와 매핑해 저장한다.
     *
     * 트랜잭션: 쓰기. Keycloak 생성 실패 시 로컬 저장은 수행하지 않는다. 로컬 저장 실패 시 생성된 Keycloak 사용자는 삭제를 시도한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 생성 중단.
     * - 소속 코드 없음 또는 타입 불일치: UserException(400 매핑), 생성 중단.
     * - Keycloak 사용자 중복 또는 생성 실패: BusinessException 계열, 로컬 저장 전 중단.
     * - 로컬 저장 실패: RuntimeException, 트랜잭션 롤백 및 Keycloak 사용자 삭제 시도.
     */
    @Transactional
    public CreateUserResult createUser(Jwt jwt, CreateUserCommand command) {
        JwtClaims.requireAdmin(jwt);
        resolveAndValidateTenancy(command.tenancyCode(), command.tenancy());

        String initialPassword = issueInitialPassword(command);
        CreateUserIdentityCommand identityCommand = new CreateUserIdentityCommand(
                command.employeeNumber(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                command.tenancy(),
                initialPassword
        );

        UserIdentity identity = userIdentityManager.create(identityCommand);
        User user = User.createPending(
                identity.keycloakId(),
                identity.employeeNumber(),
                identity.email(),
                identity.displayName(),
                identity.tenancyCode(),
                identity.position(),
                identity.role(),
                identity.tenancy()
        );

        try {
            User savedUser = userRepository.save(user);
            String responsePassword = command.passwordIssueMode() == PasswordIssueMode.AUTO ? initialPassword : null;
            return new CreateUserResult(savedUser, responsePassword);
        } catch (RuntimeException ex) {
            try {
                userIdentityManager.delete(identity.keycloakId());
            } catch (RuntimeException deleteEx) {
                ex.addSuppressed(deleteEx);
            }
            throw ex;
        }
    }

    /**
     * 관리자 요청으로 사용자의 Keycloak 비밀번호를 임시 비밀번호로 초기화한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 로컬 사용자 존재 여부를 keycloakId로 확인한다.
     * 3) 서버에서 임시 비밀번호를 생성한다.
     * 4) 로컬 사용자 상태를 PENDING으로 변경해 저장한다.
     * 5) 트랜잭션 커밋 후 Keycloak에 temporary credential로 설정한다(외부 호출).
     *
     * 트랜잭션: 쓰기. 로컬 저장 커밋이 성공한 뒤 Keycloak을 동기화한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 초기화 중단.
     * - 로컬 사용자 없음: UserException(404 매핑), 초기화 중단.
     * - Keycloak 초기화 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public ResetPasswordResult resetPassword(Jwt jwt, String keycloakId) {
        JwtClaims.requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        String temporaryPassword = issueTemporaryPassword();

        user.markPasswordResetRequired();
        User savedUser = userRepository.save(user);
        runAfterCommit("Keycloak 임시 비밀번호 설정", () -> userIdentityManager.resetPassword(keycloakId, temporaryPassword));

        return new ResetPasswordResult(savedUser, temporaryPassword);
    }

    /**
     * 관리자 요청으로 Keycloak 사용자의 enabled 값을 토글하고 로컬 정지 상태를 동기화한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 로컬 사용자를 keycloakId로 조회한다.
     * 3) UserIdentityManager로 현재 Keycloak enabled 값을 조회한다(외부 조회).
     * 4) 반전 결과를 로컬 사용자 상태에 반영해 저장한다.
     * 5) 트랜잭션 커밋 후 Keycloak enabled 값을 반전 결과로 변경한다(외부 호출).
     *
     * 트랜잭션: 쓰기. 로컬 저장 커밋이 성공한 뒤 Keycloak을 동기화한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: UserException(403 매핑), 토글 중단.
     * - 로컬 사용자 없음: UserException(404 매핑), 토글 중단.
     * - Keycloak 상태 변경 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public User toggleSuspension(Jwt jwt, String keycloakId) {
        JwtClaims.requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        UserIdentityState currentState = userIdentityManager.findState(keycloakId);
        UserIdentityState targetState = new UserIdentityState(!currentState.enabled(), currentState.passwordUpdateRequired());

        user.applyIdentityState(targetState);
        User savedUser = userRepository.save(user);
        runAfterCommit("Keycloak enabled 상태 변경", () -> userIdentityManager.updateEnabled(keycloakId, targetState.enabled()));
        return savedUser;
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

    private UserTenancy resolveTenancy(String tenancyCode) {
        Tenancy tenancy = tenancyRepository.findByCode(tenancyCode)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_TENANCY_NOT_FOUND));

        return UserTenancy.fromClaim(tenancy.type().name())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_UNSUPPORTED_TENANCY));
    }

    private UserTenancy resolveAndValidateTenancy(String tenancyCode, UserTenancy requestedTenancy) {
        UserTenancy resolvedTenancy = resolveTenancy(tenancyCode);
        if (resolvedTenancy != requestedTenancy) {
            throw new UserException(UserErrorCode.USER_TENANCY_MISMATCH);
        }

        return resolvedTenancy;
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
