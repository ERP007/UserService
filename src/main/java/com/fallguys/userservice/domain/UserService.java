package com.fallguys.userservice.domain;

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
     * 2) Keycloak sub와 매핑되는 로컬 사용자가 없으면 ACTIVE 상태로 생성한다.
     * 3) 기존 사용자는 토큰 기반 프로필과 권한 필드를 최신 값으로 갱신한다.
     *
     * 트랜잭션: 쓰기. 신규 사용자는 저장하고 기존 사용자는 JPA 변경 감지로 갱신한다.
     *
     * 예외:
     * - subject 누락: 컨트롤러에서 이 메서드 호출 전에 차단한다.
     * - 필수 Claim 누락 또는 미지원 값: ResponseStatusException(403), 트랜잭션 롤백.
     */
    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        SessionClaims claims = SessionClaims.from(jwt, resolveRole(jwt), resolveTenancy(jwt));

        return userRepository.findByKeycloakId(claims.keycloakId())
                .map(user -> {
                    user.updateSessionClaims(
                            claims.employeeNumber(),
                            claims.email(),
                            claims.displayName(),
                            claims.tenancyCode(),
                            claims.position(),
                            claims.role(),
                            claims.tenancy()
                    );
                    return userRepository.save(user);
                })
                .orElseGet(() -> userRepository.save(createUserFromClaims(claims)));
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

    private User createUserFromClaims(SessionClaims claims) {
        return User.create(
                claims.keycloakId(),
                claims.employeeNumber(),
                claims.email(),
                claims.displayName(),
                claims.tenancyCode(),
                claims.position(),
                claims.role(),
                claims.tenancy()
        );
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
