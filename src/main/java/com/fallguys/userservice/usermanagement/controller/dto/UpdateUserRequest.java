package com.fallguys.userservice.usermanagement.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.usermanagement.domain.UpdateUserCommand;

public record UpdateUserRequest(
        String email,
        @JsonProperty("display_name")
        @JsonAlias({"displayName", "name"})
        String displayName,
        @JsonProperty("tenancy_code")
        @JsonAlias("tenancyCode")
        String tenancyCode,
        String position,
        String role
) {

    public UpdateUserCommand toCommand(String keycloakId) {
        try {
            return new UpdateUserCommand(
                    keycloakId,
                    email,
                    displayName,
                    tenancyCode,
                    position,
                    parseRole(role)
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
}
