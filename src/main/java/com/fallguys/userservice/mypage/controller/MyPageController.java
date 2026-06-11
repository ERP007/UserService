package com.fallguys.userservice.mypage.controller;

import com.fallguys.userservice.mypage.controller.dto.MyPageResponse;
import com.fallguys.userservice.mypage.domain.MyPageService;
import com.fallguys.userservice.shared.domain.exception.CommonErrorCode;
import com.fallguys.userservice.shared.domain.exception.CommonException;
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
@Tag(name = "My Page", description = "마이페이지 API")
class MyPageController {

    private final MyPageService myPageService;

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 마이페이지 정보를 조회합니다.")
    @SecurityRequirement(name = BEARER_AUTH)
    @GetMapping("/me")
    MyPageResponse fetchMyProfile(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return MyPageResponse.from(myPageService.findMyPage(authenticatedJwt));
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new CommonException(CommonErrorCode.AUTHENTICATION_REQUIRED);
        }

        return jwt;
    }
}
