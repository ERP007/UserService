package com.fallguys.userservice.shared.controller;

import com.fallguys.userservice.shared.controller.dto.SessionResponse;
import com.fallguys.userservice.shared.domain.SessionService;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fallguys.userservice.shared.infrastructure.swagger.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Session", description = "사용자 세션 API")
class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "세션 정보 확인", description = "Access Token Claim을 확인하고 로컬 사용자 세션 정보를 동기화합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @GetMapping("/session")
    SessionResponse session(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);

        sessionService.synchronizeSession(authenticatedJwt);
        return SessionResponse.from(authenticatedJwt);
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new UserException(UserErrorCode.USER_AUTHENTICATION_REQUIRED);
        }

        return jwt;
    }
}
