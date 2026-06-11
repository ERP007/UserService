package com.fallguys.userservice.mypage.domain;

import com.fallguys.userservice.shared.domain.SessionService;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final MyPageRepository userRepository;
    private final UserIdentityManager userIdentityManager;
    private final SessionService sessionService;

    /**
     * 로그인한 사용자의 마이페이지 정보를 조회한다.
     *
     * 흐름:
     * 1) Gateway가 Relay한 JWT의 subject를 Keycloak ID로 사용한다.
     * 2) 공통 SessionService로 토큰 Claim, 로그인 메타데이터, Keycloak password credential 생성일을 로컬 사용자에 동기화한다.
     * 3) Keycloak enabled/required action 상태를 확인해 로컬 사용자 상태를 보정한다.
     * 4) PENDING 또는 SUSPENDED 상태인지 확인한 뒤 마이페이지 표시 정보를 반환한다.
     *
     * 트랜잭션: 쓰기. 마이페이지 조회 직전에 Keycloak 기준 메타데이터를 로컬 DB에 동기화한다.
     * 접근 차단용 UserException은 동기화 저장을 롤백하지 않는다.
     *
     * 예외:
     * - 로컬 사용자 없음: UserException(404 매핑), 조회 중단.
     * - Keycloak credential 또는 상태 조회 실패: BusinessException 계열, 트랜잭션 롤백.
     * - PENDING 또는 SUSPENDED 사용자: UserException(403 매핑), 조회 중단. 단, 앞선 동기화 저장은 커밋된다.
     */
    @Transactional(noRollbackFor = UserException.class)
    public UserDetail findMyPage(Jwt jwt) {
        User user = sessionService.synchronizeSessionWithPasswordCredential(jwt);
        UserIdentityState identityState = userIdentityManager.findState(jwt.getSubject());
        UserStatus statusBeforeIdentitySync = user.getStatus();
        user.applyIdentityState(identityState);
        if (user.getStatus() != statusBeforeIdentitySync) {
            user = userRepository.save(user);
        }

        requireMyPageAccessible(user.getStatus());
        return userRepository.findDetailByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
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
     * - PENDING 또는 SUSPENDED 상태: UserException(403 매핑), 마이페이지 조회 중단.
     */
    private void requireMyPageAccessible(UserStatus status) {
        if (status == UserStatus.PENDING) {
            throw new UserException(UserErrorCode.USER_MYPAGE_PASSWORD_CHANGE_REQUIRED);
        }
        if (status == UserStatus.SUSPENDED) {
            throw new UserException(UserErrorCode.USER_SUSPENDED);
        }
    }
}
