package com.fallguys.userservice.usermanagement.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fallguys.userservice.usermanagement.domain.UpdateUserCommand;
import com.fallguys.userservice.shared.domain.model.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static UserRole parseRole(String value) {
        return UserRole.fromClaim(value)
                .orElseThrow(() -> new IllegalArgumentException("role is missing or unsupported"));
    }
}
