package com.fallguys.userservice.usermanagement.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.usermanagement.domain.UpdateUserCommand;

public record UpdateUserRequest(
        String email,
        @JsonProperty("display_name")
        @JsonAlias({"displayName", "name"})
        String displayName,
        @JsonProperty("tenancy_code")
        @JsonAlias("tenancyCode")
        String tenancyCode,
        @JsonProperty("tenancy_name")
        @JsonAlias("tenancyName")
        String tenancyName,
        String position,
        String role,
        String tenancy
) {

    public UpdateUserCommand toCommand(String keycloakId) {
        try {
            UserRole parsedRole = parseRole(role);
            return new UpdateUserCommand(
                    keycloakId,
                    email,
                    displayName,
                    tenancyCode,
                    tenancyName,
                    position,
                    parsedRole,
                    parseTenancy(tenancy, parsedRole)
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

    private static UserTenancy parseTenancy(
            String value,
            UserRole role
    ) {
        if (value == null || value.isBlank()) {
            return UserTenancy.fromRole(role);
        }

        return UserTenancy.fromClaim(value)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_INVALID_REQUEST));
    }
}
