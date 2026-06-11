package com.fallguys.userservice.usermanagement.controller;

import com.fallguys.userservice.usermanagement.controller.dto.CreateUserRequest;
import com.fallguys.userservice.usermanagement.controller.dto.CreateUserResponse;
import com.fallguys.userservice.usermanagement.controller.dto.ResetPasswordResponse;
import com.fallguys.userservice.usermanagement.controller.dto.SuspensionRequest;
import com.fallguys.userservice.usermanagement.controller.dto.SuspensionResponse;
import com.fallguys.userservice.usermanagement.controller.dto.UpdateUserRequest;
import com.fallguys.userservice.usermanagement.controller.dto.UserDetailResponse;
import com.fallguys.userservice.usermanagement.controller.dto.UserListResponse;
import com.fallguys.userservice.usermanagement.controller.dto.UserSearchRequest;
import com.fallguys.userservice.usermanagement.domain.UserManagementService;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.fallguys.userservice.shared.infrastructure.swagger.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "관리자 사용자 관리 API")
class UserManagementController {

    private final UserManagementService userManagementService;

    @Operation(summary = "사용자 목록 조회", description = "관리자가 사용자 목록을 검색, 필터링, 정렬, 페이지 조건으로 조회합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @GetMapping
    UserListResponse fetchUserList(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
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

        return UserListResponse.from(userManagementService.findUsers(authenticatedJwt, request.toQuery()));
    }

    @Operation(summary = "사용자 상세 조회", description = "관리자가 Keycloak ID로 사용자 상세 정보를 조회합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @GetMapping("/{keycloakId}")
    UserDetailResponse fetchUserDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserDetailResponse.from(userManagementService.findUserDetail(authenticatedJwt, keycloakId));
    }

    @Operation(summary = "사용자 정보 수정", description = "관리자가 사용자 기본 정보를 수정합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PatchMapping("/{keycloakId}")
    UserDetailResponse updateUser(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId,
            @RequestBody UpdateUserRequest request
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserDetailResponse.from(userManagementService.updateUser(authenticatedJwt, request.toCommand(keycloakId)));
    }

    @Operation(summary = "사용자 생성", description = "관리자가 Keycloak과 로컬 DB에 사용자를 생성합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    CreateUserResponse createUser(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateUserRequest request
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return CreateUserResponse.from(userManagementService.createUser(authenticatedJwt, request.toCommand()));
    }

    @Operation(summary = "사용자 비밀번호 초기화", description = "관리자가 사용자 임시 비밀번호를 재발급합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PatchMapping("/{keycloakId}/reset-password")
    ResetPasswordResponse resetPassword(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return ResetPasswordResponse.from(userManagementService.resetPassword(authenticatedJwt, keycloakId));
    }

    @Operation(summary = "사용자 정지 상태 변경", description = "관리자가 사용자 정지 여부를 지정합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PatchMapping("/{keycloakId}/suspension")
    SuspensionResponse updateSuspension(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String keycloakId,
            @RequestBody SuspensionRequest request
    ) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return SuspensionResponse.from(userManagementService.updateSuspension(
                authenticatedJwt,
                keycloakId,
                requiredSuspended(request)
        ));
    }

    private boolean requiredSuspended(SuspensionRequest request) {
        if (request == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return request.requiredSuspended();
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new UserException(UserErrorCode.USER_AUTHENTICATION_REQUIRED);
        }

        return jwt;
    }
}
