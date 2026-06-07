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
