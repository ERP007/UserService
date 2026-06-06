package com.fallguys.userservice.controller;

import com.fallguys.userservice.controller.dto.SessionResponse;
import com.fallguys.userservice.domain.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping("/session")
    SessionResponse me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing");
        }

        userService.getOrCreateUser(jwt);
        return SessionResponse.from(jwt);
    }
}
