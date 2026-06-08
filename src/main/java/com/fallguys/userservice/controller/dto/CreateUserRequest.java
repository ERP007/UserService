package com.fallguys.userservice.controller.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.domain.CreateUserCommand;
import com.fallguys.userservice.domain.PasswordIssueMode;
import com.fallguys.userservice.domain.UserRole;
import com.fallguys.userservice.domain.UserTenancy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CreateUserRequest(
        @JsonProperty("employee_no")
        @JsonAlias("employeeNo")
        String employeeNumber,
        String email,
        @JsonProperty("display_name")
        @JsonAlias({"displayName", "name"})
        String displayName,
        @JsonProperty("tenancy_code")
        @JsonAlias("tenancyCode")
        String tenancyCode,
        String position,
        String role,
        String tenancy,
        @JsonProperty("password_issue_mode")
        @JsonAlias("passwordIssueMode")
        String passwordIssueMode,
        @JsonProperty("initial_password")
        @JsonAlias("initialPassword")
        String initialPassword
) {

    public CreateUserCommand toCommand() {
        try {
            return new CreateUserCommand(
                    employeeNumber,
                    email,
                    displayName,
                    tenancyCode,
                    position,
                    parseRole(role),
                    parseTenancy(tenancy),
                    parsePasswordIssueMode(passwordIssueMode),
                    initialPassword
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static UserRole parseRole(String value) {
        return UserRole.fromClaim(value)
                .orElseThrow(() -> new IllegalArgumentException("role is missing or unsupported"));
    }

    private static UserTenancy parseTenancy(String value) {
        return UserTenancy.fromClaim(value)
                .orElseThrow(() -> new IllegalArgumentException("tenancy is missing or unsupported"));
    }

    private static PasswordIssueMode parsePasswordIssueMode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("passwordIssueMode is required");
        }

        try {
            return PasswordIssueMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("passwordIssueMode is unsupported");
        }
    }
}
