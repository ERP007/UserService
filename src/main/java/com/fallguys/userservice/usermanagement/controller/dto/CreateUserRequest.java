package com.fallguys.userservice.usermanagement.controller.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.usermanagement.domain.CreateUserCommand;
import com.fallguys.userservice.usermanagement.domain.PasswordIssueMode;

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
        } catch (UserException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST, ex);
        }
    }

    private static UserRole parseRole(String value) {
        return UserRole.fromClaim(value)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_ROLE_UNSUPPORTED));
    }

    private static UserTenancy parseTenancy(String value) {
        return UserTenancy.fromClaim(value)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_UNSUPPORTED_TENANCY));
    }

    private static PasswordIssueMode parsePasswordIssueMode(String value) {
        if (value == null || value.isBlank()) {
            throw new UserException(UserErrorCode.USER_PASSWORD_ISSUE_MODE_REQUIRED);
        }

        try {
            return PasswordIssueMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new UserException(UserErrorCode.USER_PASSWORD_ISSUE_MODE_UNSUPPORTED, ex);
        }
    }
}
