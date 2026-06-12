package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserIdentityManager userIdentityManager;

    /**
     * Gateway가 Relay한 Keycloak Access Token과 매핑되는 로컬 사용자를 조회하거나 생성한다.
     *
     * 흐름:
     * 1) JWT에서 사용자 ID, 소속, 권한 Claim을 추출한다.
     * 2) JWT의 auth_time/iat와 sid로 마지막 로그인 시각과 로그인 세션 ID를 계산한다.
     * 3) 필요한 경우 Keycloak password credential의 createdDate를 조회해 비밀번호 마지막 변경 시각을 계산한다(외부 호출).
     * 4) Keycloak sub와 매핑되는 로컬 사용자가 없으면 ACTIVE 상태로 생성한다.
     * 5) 기존 사용자는 토큰 기반 프로필·권한·로그인 메타데이터를 최신 값으로 갱신한다.
     *
     * 트랜잭션: 쓰기. 외부 credential 조회 실패 시 저장 전 중단되어 로컬 값은 변경되지 않는다.
     *
     * 예외:
     * - subject 누락: 컨트롤러에서 이 메서드 호출 전에 차단한다.
     * - 필수 Claim 누락 또는 미지원 값: UserException(403 매핑), 트랜잭션 롤백.
     * - Keycloak credential 조회 실패: BusinessException 계열, 트랜잭션 롤백.
     */
    @Transactional
    public User synchronizeSession(Jwt jwt) {
        return synchronizeAuthenticatedUser(jwt, false);
    }

    /**
     * 인증된 사용자 정보를 동기화하되 Keycloak password credential 생성일을 강제로 다시 조회한다.
     *
     * 흐름:
     * 1) 기본 세션 동기화와 동일하게 JWT Claim과 로그인 메타데이터를 반영한다.
     * 2) 로그인 세션 ID 변경 여부와 무관하게 Keycloak password credential 생성일을 조회한다(외부 호출).
     * 3) 조회한 비밀번호 변경 시각을 로컬 사용자에 반영한다.
     *
     * 트랜잭션: 쓰기. credential 조회 또는 저장 실패 시 동기화는 롤백된다.
     *
     * 예외:
     * - 필수 Claim 누락 또는 미지원 값: UserException(403 매핑), 트랜잭션 롤백.
     * - Keycloak credential 조회 실패: BusinessException 계열, 트랜잭션 롤백.
     */
    @Transactional
    public User synchronizeSessionWithPasswordCredential(Jwt jwt) {
        return synchronizeAuthenticatedUser(jwt, true);
    }

    private User synchronizeAuthenticatedUser(Jwt jwt, boolean forcePasswordChangedSync) {
        SessionClaims claims = SessionClaims.from(jwt, JwtClaims.role(jwt), JwtClaims.tenancy(jwt));
        Instant loginAt = resolveLoginAt(jwt);
        String loginSessionId = jwt.getClaimAsString("sid");

        return sessionRepository.findByKeycloakId(claims.keycloakId())
                .map(user -> {
                    Instant passwordChangedAt = shouldSyncPasswordChangedAt(user, loginSessionId, forcePasswordChangedSync)
                            ? findPasswordChangedAt(claims.keycloakId()).orElse(null)
                            : user.getPasswordChangedAt();
                    return syncExistingSessionUser(user, claims, loginAt, loginSessionId, passwordChangedAt);
                })
                .orElseGet(() -> sessionRepository.save(createUserFromClaims(
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

        return sessionRepository.save(user);
    }

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
}
