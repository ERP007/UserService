package com.fallguys.userservice.domain;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private static final int KEYCLOAK_SYNC_MAX_ATTEMPTS = 3;
    private static final int MAX_BATCH_USER_LOOKUP_SIZE = 100;

    private final UserRepository userRepository;
    private final TenancyRepository tenancyRepository;
    private final UserIdentityManager userIdentityManager;

    /**
     * Gateway가 Relay한 Keycloak Access Token과 매핑되는 로컬 사용자를 조회하거나 생성한다.
     *
     * 흐름:
     * 1) 공통 동기화 로직에 JWT를 전달한다.
     * 2) JWT에서 사용자 ID, 소속, 권한 Claim을 추출한다.
     * 3) JWT의 auth_time/iat와 sid로 마지막 로그인 시각과 로그인 세션 ID를 계산한다.
     * 4) 필요한 경우 Keycloak password credential의 createdDate를 조회해 비밀번호 마지막 변경 시각을 계산한다(외부 호출).
     * 5) Keycloak sub와 매핑되는 로컬 사용자가 없으면 ACTIVE 상태로 생성한다.
     * 6) 기존 사용자는 토큰 기반 프로필·권한·로그인 메타데이터를 최신 값으로 갱신한다.
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
        return syncAuthenticatedUser(jwt, false);
    }

    /**
     * 인증된 JWT를 기준으로 Keycloak의 현재 사용자 상태를 로컬 User DB에 반영한다.
     *
     * 흐름:
     * 1) JWT Claim을 SessionClaims로 변환해 Keycloak ID, 사번, 소속, 권한을 추출한다.
     * 2) JWT auth_time/iat와 sid로 마지막 로그인 시각과 세션 ID를 계산한다.
     * 3) 기존 사용자가 있으면 토큰 Claim과 로그인 메타데이터를 반영하고, 필요한 경우 password credential 생성일을 조회한다.
     * 4) 로컬 사용자가 없으면 Keycloak 기준 Claim과 credential 생성일로 신규 사용자를 생성한다.
     *
     * 트랜잭션: 호출한 public 메서드의 트랜잭션에 참여한다. Keycloak credential 조회 실패 시 로컬 저장 전 롤백된다.
     *
     * 예외:
     * - 필수 JWT Claim 누락 또는 미지원 값: ResponseStatusException(403), 로컬 저장 중단.
     * - Keycloak credential 조회 실패: BusinessException 계열, 로컬 저장 중단.
     */
    private User syncAuthenticatedUser(Jwt jwt, boolean forcePasswordChangedSync) {
        SessionClaims claims = SessionClaims.from(jwt, resolveRole(jwt), resolveTenancy(jwt));
        Instant loginAt = resolveLoginAt(jwt);
        String loginSessionId = jwt.getClaimAsString("sid");

        return userRepository.findByKeycloakId(claims.keycloakId())
                .map(user -> {
                    Instant passwordChangedAt = shouldSyncPasswordChangedAt(user, loginSessionId, forcePasswordChangedSync)
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

    /**
     * 이미 존재하는 로컬 사용자에 Keycloak 토큰 기반 프로필과 로그인 메타데이터를 반영한다.
     *
     * 흐름:
     * 1) 토큰 Claim의 사번, 이메일, 이름, 소속, 직급, 역할을 로컬 사용자에 반영한다.
     * 2) JWT에서 계산한 마지막 로그인 시각과 세션 ID를 반영한다.
     * 3) Keycloak password credential 기준 비밀번호 마지막 변경 시각을 반영한다.
     * 4) 변경이 있는 경우에만 저장해 불필요한 update 쿼리를 줄인다.
     *
     * 트랜잭션: 호출한 public 메서드의 쓰기 트랜잭션에 참여한다. 저장 실패 시 현재 동기화는 롤백된다.
     *
     * 예외:
     * - UserRepository 저장 실패: RuntimeException, 트랜잭션 롤백.
     */
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

    /**
     * Keycloak password credential 생성일을 다시 조회해야 하는지 판단한다.
     *
     * 흐름:
     * 1) 마이페이지처럼 강제 동기화가 필요한 호출이면 항상 true를 반환한다.
     * 2) 로컬 비밀번호 변경 일자가 없거나 PENDING 상태면 Keycloak 값을 다시 확인한다.
     * 3) 로그인 세션 ID가 없거나 직전 저장 세션과 다르면 새 로그인 흐름으로 보고 다시 확인한다.
     *
     * 트랜잭션: 상태를 변경하지 않는 판단 로직이며, 실제 외부 조회와 저장은 호출자가 수행한다.
     *
     * 예외: 없음.
     */
    private boolean shouldSyncPasswordChangedAt(User user, String loginSessionId, boolean forcePasswordChangedSync) {
        return forcePasswordChangedSync
                || user.getPasswordChangedAt() == null
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
     * 사번 목록으로 사용자 이름과 직급을 배치 조회한다.
     *
     * 흐름:
     * 1) 요청 사번 목록에서 공백 값을 제거하고 중복을 정리한 뒤 최대 조회 개수를 검증한다.
     * 2) UserRepository에 정리된 사번 목록을 전달해 IN 조건으로 한 번에 조회한다.
     * 3) 요청 순서를 기준으로 조회된 사용자와 찾지 못한 사번을 분리해 반환한다.
     *
     * 트랜잭션: 읽기 전용. 발주 서비스 등 내부 호출자가 담당자 표시 정보를 조회할 때 사용하며 상태를 변경하지 않는다.
     *
     * 예외:
     * - 사번 목록 누락, 전부 공백, 최대 개수 초과: ResponseStatusException(400), 조회 중단.
     */
    @Transactional(readOnly = true)
    public BatchUserListResult findBatchUsers(List<String> employeeNumbers) {
        List<String> requestedEmployeeNumbers = normalizeEmployeeNumbers(employeeNumbers);
        List<String> lookupEmployeeNumbers = requestedEmployeeNumbers.stream()
                .map(this::employeeNumberKey)
                .toList();
        List<BatchUser> foundUsers = userRepository.findBatchUsersByEmployeeNumbers(lookupEmployeeNumbers);
        Map<String, BatchUser> foundByEmployeeNumber = new LinkedHashMap<>();
        foundUsers.forEach(user -> foundByEmployeeNumber.putIfAbsent(employeeNumberKey(user.employeeNumber()), user));

        List<BatchUser> orderedUsers = requestedEmployeeNumbers.stream()
                .map(employeeNumber -> foundByEmployeeNumber.get(employeeNumberKey(employeeNumber)))
                .filter(Objects::nonNull)
                .toList();
        List<String> notFoundEmployeeNumbers = requestedEmployeeNumbers.stream()
                .filter(employeeNumber -> !foundByEmployeeNumber.containsKey(employeeNumberKey(employeeNumber)))
                .toList();

        return new BatchUserListResult(orderedUsers, notFoundEmployeeNumbers);
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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 조회 중단.
     * - 사용자 없음: ResponseStatusException(404), 조회 중단.
     */
    @Transactional(readOnly = true)
    public UserDetail findUserDetail(Jwt jwt, String keycloakId) {
        requireAdmin(jwt);

        return userRepository.findDetailByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    /**
     * 로그인한 사용자의 마이페이지 정보를 조회한다.
     *
     * 흐름:
     * 1) Gateway가 Relay한 JWT의 subject를 Keycloak ID로 사용한다.
     * 2) 토큰 Claim, 마지막 로그인 메타데이터, Keycloak password credential 생성일을 로컬 사용자에 동기화한다.
     * 3) Keycloak enabled/required action 상태를 확인해 로컬 사용자 상태를 보정한다.
     * 4) PENDING 또는 SUSPENDED 상태인지 확인한 뒤 마이페이지 표시 정보를 반환한다.
     *
     * 트랜잭션: 쓰기. 마이페이지 조회 직전에 Keycloak 기준 메타데이터를 로컬 DB에 동기화한다.
     * 접근 차단용 ResponseStatusException은 동기화 저장을 롤백하지 않는다.
     *
     * 예외:
     * - 로컬 사용자 없음: ResponseStatusException(404), 조회 중단.
     * - Keycloak credential 또는 상태 조회 실패: BusinessException 계열, 트랜잭션 롤백.
     * - PENDING 또는 SUSPENDED 사용자: ResponseStatusException(403), 조회 중단. 단, 앞선 동기화 저장은 커밋된다.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public UserDetail findMyPage(Jwt jwt) {
        User user = syncAuthenticatedUser(jwt, true);
        UserIdentityState identityState = userIdentityManager.findState(jwt.getSubject());
        UserStatus statusBeforeIdentitySync = user.getStatus();
        user.applyIdentityState(identityState);
        if (user.getStatus() != statusBeforeIdentitySync) {
            user = userRepository.save(user);
        }

        requireMyPageAccessible(user.getStatus());
        return userRepository.findDetailByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 수정 중단.
     * - 사용자 없음: ResponseStatusException(404), 수정 중단.
     * - 소속 코드 없음: ResponseStatusException(400), 수정 중단.
     * - Keycloak 수정 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public UserDetail updateUser(Jwt jwt, UpdateUserCommand command) {
        requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(command.keycloakId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
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
        runAfterCommit("Keycloak 사용자 정보 수정", () -> userIdentityManager.update(command, tenancy));

        return userRepository.findDetailByKeycloakId(command.keycloakId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 생성 중단.
     * - 소속 코드 없음 또는 타입 불일치: ResponseStatusException(400), 생성 중단.
     * - Keycloak 사용자 중복 또는 생성 실패: BusinessException 계열, 로컬 저장 전 중단.
     * - 로컬 저장 실패: RuntimeException, 트랜잭션 롤백 및 Keycloak 사용자 삭제 시도.
     */
    @Transactional
    public CreateUserResult createUser(Jwt jwt, CreateUserCommand command) {
        requireAdmin(jwt);
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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 초기화 중단.
     * - 로컬 사용자 없음: ResponseStatusException(404), 초기화 중단.
     * - Keycloak 초기화 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public ResetPasswordResult resetPassword(Jwt jwt, String keycloakId) {
        requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
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
     * - 관리자 Claim 조건 불만족: ResponseStatusException(403), 토글 중단.
     * - 로컬 사용자 없음: ResponseStatusException(404), 토글 중단.
     * - Keycloak 상태 변경 실패: BusinessException 계열, 로컬 저장 커밋 후 실패 로그 및 예외 전파.
     */
    @Transactional
    public User toggleSuspension(Jwt jwt, String keycloakId) {
        requireAdmin(jwt);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
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

    private List<String> normalizeEmployeeNumbers(List<String> employeeNumbers) {
        if (employeeNumbers == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사번 목록을 입력해주세요.");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        employeeNumbers.stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(employeeNumber -> normalized.putIfAbsent(employeeNumberKey(employeeNumber), employeeNumber));

        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사번 목록을 입력해주세요.");
        }
        if (normalized.size() > MAX_BATCH_USER_LOOKUP_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사번은 한 번에 최대 100개까지 조회할 수 있습니다.");
        }

        return List.copyOf(normalized.values());
    }

    private String employeeNumberKey(String employeeNumber) {
        return employeeNumber.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 마이페이지 접근 가능한 사용자 상태인지 검증한다.
     *
     * 흐름:
     * 1) PENDING 사용자는 Keycloak 비밀번호 변경 완료 전 상태이므로 차단한다.
     * 2) SUSPENDED 사용자는 서비스 접근이 정지된 상태이므로 차단한다.
     *
     * 트랜잭션: 상태를 변경하지 않는다.
     *
     * 예외:
     * - PENDING 또는 SUSPENDED 상태: ResponseStatusException(403), 마이페이지 조회 중단.
     */
    private void requireMyPageAccessible(UserStatus status) {
        if (status == UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비밀번호 변경 전까지 마이페이지에 접근할 수 없습니다.");
        }
        if (status == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "정지된 사용자는 마이페이지에 접근할 수 없습니다.");
        }
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

    private UserTenancy resolveTenancy(String tenancyCode) {
        Tenancy tenancy = tenancyRepository.findByCode(tenancyCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속을 찾을 수 없습니다."));

        return UserTenancy.fromClaim(tenancy.type().name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 소속 타입입니다."));
    }

    private UserTenancy resolveAndValidateTenancy(String tenancyCode, UserTenancy requestedTenancy) {
        UserTenancy resolvedTenancy = resolveTenancy(tenancyCode);
        if (resolvedTenancy != requestedTenancy) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속 코드와 소속 타입이 일치하지 않습니다.");
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
