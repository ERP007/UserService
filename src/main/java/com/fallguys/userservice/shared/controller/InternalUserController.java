package com.fallguys.userservice.shared.controller;

import com.fallguys.userservice.shared.controller.dto.BatchUserListRequest;
import com.fallguys.userservice.shared.controller.dto.BatchUserListResponse;
import com.fallguys.userservice.shared.controller.dto.BatchUserItemResponse;
import com.fallguys.userservice.shared.domain.InternalUserService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fallguys.userservice.shared.infrastructure.swagger.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal User", description = "서비스 간 사용자 조회 API")
class InternalUserController {

    private final InternalUserService internalUserService;

    @Operation(summary = "사용자 배치 조회", description = "사번 목록으로 사용자 이름과 직급을 조회합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @PostMapping("/batch-userList")
    BatchUserListResponse batchUserList(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestBody BatchUserListRequest request
    ) {
        requireJwt(jwt);
        return BatchUserListResponse.from(internalUserService.findBatchUsers(request.employeeNumbers()));
    }

    @Operation(summary = "사용자 단건 조회", description = "사번으로 사용자 이름과 직급을 조회합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @GetMapping("/{employeeNo}")
    BatchUserItemResponse user(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String employeeNo
    ) {
        requireJwt(jwt);
        return BatchUserItemResponse.from(internalUserService.findByEmployeeNum(employeeNo));
    }

    private void requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new UserException(UserErrorCode.USER_AUTHENTICATION_REQUIRED);
        }
    }
}
