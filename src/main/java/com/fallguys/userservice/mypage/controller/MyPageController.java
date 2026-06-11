package com.fallguys.userservice.mypage.controller;

import com.fallguys.userservice.mypage.controller.dto.MyPageResponse;
import com.fallguys.userservice.mypage.domain.MyPageService;
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
class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/me")
    MyPageResponse fetchMyProfile(@AuthenticationPrincipal Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return MyPageResponse.from(myPageService.findMyPage(authenticatedJwt));
    }

    private Jwt requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing");
        }

        return jwt;
    }
}
