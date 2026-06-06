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
                .orElseGet(() -> userRepository.save(createUser(claims)));
    }

    private User createUser(SessionClaims claims) {
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
