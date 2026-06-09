package com.fallguys.userservice.controller;

import com.fallguys.userservice.controller.dto.CreateUserRequest;
import com.fallguys.userservice.controller.dto.CreateUserResponse;
import com.fallguys.userservice.controller.dto.ResetPasswordResponse;
import com.fallguys.userservice.controller.dto.SessionResponse;
import com.fallguys.userservice.controller.dto.SuspendToggleResponse;
import com.fallguys.userservice.controller.dto.UpdateUserRequest;
import com.fallguys.userservice.controller.dto.UserDetailResponse;
import com.fallguys.userservice.controller.dto.UserListResponse;
import com.fallguys.userservice.controller.dto.UserSearchRequest;
import com.fallguys.userservice.domain.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping
    UserListResponse users(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(name = "tenancy_code", defaultValue = "ALL") String tenancyCode,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "employeeNo") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        UserSearchRequest request = new UserSearchRequest(
                page,
                size,
                keyword,
                role,
                tenancyCode,
                status,
                sortBy,
                sortDirection
        );

        return UserListResponse.from(userService.findUsers(authenticatedJwt, request.toQuery()));
    }

    @GetMapping("/session")
    SessionResponse me(@AuthenticationPrincipal Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);

        userService.getOrCreateUser(authenticatedJwt);
        return SessionResponse.from(authenticatedJwt);
    }

    @GetMapping("/{keycloakId}")
    UserDetailResponse user(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserDetailResponse.from(userService.findUserDetail(authenticatedJwt, keycloakId));
    }

    @PatchMapping("/{keycloakId}")
    UserDetailResponse updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId,
            @RequestBody UpdateUserRequest request
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserDetailResponse.from(userService.updateUser(authenticatedJwt, request.toCommand(keycloakId)));
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    CreateUserResponse createUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateUserRequest request
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return CreateUserResponse.from(userService.createUser(authenticatedJwt, request.toCommand()));
    }

    @PatchMapping("/{keycloakId}/reset-password")
    ResetPasswordResponse resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return ResetPasswordResponse.from(userService.resetPassword(authenticatedJwt, keycloakId));
    }

    @PatchMapping("/{keycloakId}/suspendToggle")
    SuspendToggleResponse suspendToggle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return SuspendToggleResponse.from(userService.toggleSuspension(authenticatedJwt, keycloakId));
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing");
        }

        return jwt;
    }
}
