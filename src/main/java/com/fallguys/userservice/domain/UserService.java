package com.fallguys.userservice.domain;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserIdentityManager userIdentityManager;

    /**
     * Gateway가 Relay한 Keycloak Access Token과 매핑되는 로컬 사용자를 조회하거나 생성한다.
     *
     * 흐름:
     * 1) JWT에서 사용자 ID, 소속, 권한 Claim을 추출한다.
     * 2) JWT의 auth_time/iat와 sid로 마지막 로그인 시각과 로그인 세션 ID를 계산한다.
     * 3) Keycloak password credential의 createdDate를 조회해 비밀번호 마지막 변경 시각을 계산한다(외부 호출).
     * 4) Keycloak sub와 매핑되는 로컬 사용자가 없으면 ACTIVE 상태로 생성한다.
     * 5) 기존 사용자는 토큰 기반 프로필·권한·로그인 메타데이터를 최신 값으로 갱신한다.
     *
     * 트랜잭션: 쓰기. 외부 credential 조회 실패 시 저장 전 중단되어 로컬 값은 변경되지 않는다.
     *
     * 예외:
     * - subject 누락: 컨트롤러에서 이 메서드 호출 전에 차단한다.
     * - 필수 Claim 누락 또는 미지원 값: ResponseStatusException(403), 트랜잭션 롤백.
     * - Keycloak credential 조회 실패: BusinessException 계열, 트랜잭션 롤백.
     */
    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        SessionClaims claims = SessionClaims.from(jwt, resolveRole(jwt), resolveTenancy(jwt));
        Instant loginAt = resolveLoginAt(jwt);
        String loginSessionId = jwt.getClaimAsString("sid");

        return userRepository.findByKeycloakId(claims.keycloakId())
                .map(user -> {
                    Instant passwordChangedAt = shouldSyncPasswordChangedAt(user, loginSessionId)
                            ? findPasswordChangedAt(claims.keycloakId()).orElse(null)
                            : user.getPasswordChangedAt();
                    return syncExistingSessionUser(user, claims, loginAt, loginSessionId, passwordChangedAt);
                })
                .orElseGet(() -> userRepository.save(createUserFromClaims(
                        claims,
                        loginAt,
                        loginSessionId,
                        findPasswordChangedAt(claims.keycloakId()).orElse(null)
                )));
    }

    private User syncExistingSessionUser(
            User user,
            SessionClaims claims,
            Instant loginAt,
            String loginSessionId,
            Instant passwordChangedAt
    ) {
        boolean changed = user.updateSessionClaims(
                            claims.employeeNumber(),
                            claims.email(),
                            claims.displayName(),
                            claims.tenancyCode(),
                            claims.position(),
                            claims.role(),
                            claims.tenancy()
        );
        changed |= user.updateLastLogin(loginAt, loginSessionId);
        changed |= user.updatePasswordChangedAt(passwordChangedAt);

        if (!changed) {
            return user;
        }

        return userRepository.save(user);
    }

    private boolean shouldSyncPasswordChangedAt(User user, String loginSessionId) {
        return user.getPasswordChangedAt() == null
                || user.getStatus() == UserStatus.PENDING
                || !hasText(loginSessionId)
                || !loginSessionId.equals(user.getLastLoginSessionId());
    }

    private Optional<Instant> findPasswordChangedAt(String keycloakId) {
        return userIdentityManager.findPasswordChangedAt(keycloakId);
    }

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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 조회 중단.
     * - 필수 권한 Claim 누락 또는 미지원 값: ResponseStatusException(403), 조회 중단.
     */
    @Transactional(readOnly = true)
    public UserListPage findUsers(Jwt jwt, UserSearchQuery query) {
        requireAdmin(jwt);
        return userRepository.findUsers(query);
    }

    /**
     * 관리자 요청으로 Keycloak 사용자와 로컬 사용자를 함께 생성한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) UserIdentityManager로 Keycloak 사용자를 생성하고 Representation 기반 값을 돌려받는다(외부 호출).
     * 3) 생성된 Keycloak ID를 로컬 사용자와 매핑해 저장한다.
     *
     * 트랜잭션: 쓰기. Keycloak 생성 실패 시 로컬 저장은 수행하지 않는다. 로컬 저장 실패 시 생성된 Keycloak 사용자는 삭제를 시도한다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 생성 중단.
     * - Keycloak 사용자 중복 또는 생성 실패: BusinessException 계열, 로컬 저장 전 중단.
     * - 로컬 저장 실패: RuntimeException, 트랜잭션 롤백 및 Keycloak 사용자 삭제 시도.
     */
    @Transactional
    public CreateUserResult createUser(Jwt jwt, CreateUserCommand command) {
        requireAdmin(jwt);

        String initialPassword = issueInitialPassword(command);
        CreateUserCommand commandWithPassword = command.withInitialPassword(initialPassword);

        UserIdentity identity = userIdentityManager.create(commandWithPassword);
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
     * 3) 서버에서 임시 비밀번호를 생성하고 Keycloak에 temporary credential로 설정한다(외부 호출).
     * 4) 로컬 사용자 상태를 PENDING으로 변경해 다음 로그인 시 비밀번호 변경이 필요함을 반영한다.
     *
     * 트랜잭션: 쓰기. Keycloak 초기화 실패 시 로컬 상태는 변경하지 않는다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 초기화 중단.
     * - 로컬 사용자 없음: ResponseStatusException(404), 초기화 중단.
     * - Keycloak 초기화 실패: BusinessException 계열, 로컬 저장 전 중단.
     */
    @Transactional
    public ResetPasswordResult resetPassword(Jwt jwt, String keycloakId) {
        requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        String temporaryPassword = issueTemporaryPassword();

        userIdentityManager.resetPassword(keycloakId, temporaryPassword);
        user.markPasswordResetRequired();

        return new ResetPasswordResult(userRepository.save(user), temporaryPassword);
    }

    /**
     * 관리자 요청으로 Keycloak 사용자의 enabled 값을 토글하고 로컬 정지 상태를 동기화한다.
     *
     * 흐름:
     * 1) JWT Claim이 관리자 권한인지 확인한다.
     * 2) 로컬 사용자를 keycloakId로 조회한다.
     * 3) UserIdentityManager로 Keycloak enabled 값을 반전한다(외부 호출).
     * 4) 반전 결과를 로컬 사용자 상태에 반영해 저장한다.
     *
     * 트랜잭션: 쓰기. Keycloak 상태 변경 실패 시 로컬 상태는 변경하지 않는다.
     *
     * 예외:
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 토글 중단.
     * - 로컬 사용자 없음: ResponseStatusException(404), 토글 중단.
     * - Keycloak 상태 변경 실패: BusinessException 계열, 로컬 저장 전 중단.
     */
    @Transactional
    public User toggleSuspension(Jwt jwt, String keycloakId) {
        requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        UserIdentityState identityState = userIdentityManager.toggleEnabled(keycloakId);

        user.applyIdentityState(identityState);
        return userRepository.save(user);
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

    private User createUserFromClaims(
            SessionClaims claims,
            Instant loginAt,
            String loginSessionId,
            Instant passwordChangedAt
    ) {
        User user = User.create(
                claims.keycloakId(),
                claims.employeeNumber(),
                claims.email(),
                claims.displayName(),
                claims.tenancyCode(),
                claims.position(),
                claims.role(),
                claims.tenancy()
        );
        user.updateLastLogin(loginAt, loginSessionId);
        user.updatePasswordChangedAt(passwordChangedAt);
        return user;
    }

    private Instant resolveLoginAt(Jwt jwt) {
        Instant authTime = toInstant(jwt.getClaims().get("auth_time"));
        if (authTime != null) {
            return authTime;
        }
        if (jwt.getIssuedAt() != null) {
            return jwt.getIssuedAt();
        }

        return Instant.now();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof String text) {
            return toInstant(text);
        }

        return null;
    }

    private Instant toInstant(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireAdmin(Jwt jwt) {
        UserRole role = resolveRole(jwt);
        UserTenancy tenancy = resolveTenancy(jwt);
        String tenancyCode = jwt.getClaimAsString("tenancy_code");

        if (!"ADMIN".equals(tenancyCode) || role != UserRole.ADMIN || tenancy != UserTenancy.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin users can access this API");
        }
    }

    private UserRole resolveRole(Jwt jwt) {
        return UserRole.fromClaim(jwt.getClaimAsString("user_role"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "JWT user_role claim is missing or unsupported"
                ));
    }

    private UserTenancy resolveTenancy(Jwt jwt) {
        return UserTenancy.fromClaim(jwt.getClaimAsString("tenancy_type"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "JWT tenancy_type claim is missing or unsupported"
                ));
    }

}
