package com.fallguys.userservice.mypage.controller;

import com.fallguys.userservice.mypage.controller.dto.MyPageResponse;
import com.fallguys.userservice.mypage.domain.MyPageService;
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
class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/me")
    MyPageResponse fetchMyProfile(@AuthenticationPrincipal Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return MyPageResponse.from(myPageService.findMyPage(authenticatedJwt));
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new UserException(UserErrorCode.USER_AUTHENTICATION_REQUIRED);
        }

        return jwt;
    }
}
