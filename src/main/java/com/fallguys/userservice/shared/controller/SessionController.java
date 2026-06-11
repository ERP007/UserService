package com.fallguys.userservice.shared.controller;

import com.fallguys.userservice.shared.controller.dto.SessionResponse;
import com.fallguys.userservice.shared.domain.SessionService;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
class SessionController {

    private final SessionService sessionService;

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
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
