package com.fallguys.userservice.shared.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Health", description = "서비스 상태 확인 API")
class HealthController {

    @Operation(summary = "헬스 체크", description = "UserService 상태를 확인합니다.")
    @GetMapping("/health")
    String health() {
        return "user-service ok";
    }
}
