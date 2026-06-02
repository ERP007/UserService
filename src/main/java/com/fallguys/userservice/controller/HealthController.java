package com.fallguys.userservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
class HealthController {

    @GetMapping("/health")
    String health() {
        return "user-service ok";
    }
}