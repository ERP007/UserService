package com.fallguys.userservice.controller;

import com.fallguys.userservice.controller.dto.BatchUserListRequest;
import com.fallguys.userservice.controller.dto.BatchUserListResponse;
import com.fallguys.userservice.controller.dto.BatchUserItemResponse;
import com.fallguys.userservice.domain.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
class InternalUserController {

    private final UserService userService;

    @PostMapping("/batch-userList")
    BatchUserListResponse batchUserList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BatchUserListRequest request
    ) {
        requireJwt(jwt);
        return BatchUserListResponse.from(userService.findBatchUsers(request.employeeNumbers()));
    }

    @GetMapping("/{employeeNo}")
    BatchUserItemResponse user(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String employeeNo
    ) {
        requireJwt(jwt);
        return BatchUserItemResponse.from(userService.findByEmployeeNum(employeeNo));
    }

    private void requireJwt(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing");
        }
    }
}
