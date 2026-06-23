package com.fallguys.userservice.usermanagement.domain;

public record UserAuthorityChangedEvent(
        String keycloakSub,
        String employeeNo
) {
    public UserAuthorityChangedEvent {
        keycloakSub = required(keycloakSub, "keycloakSub");
        employeeNo = required(employeeNo, "employeeNo");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
