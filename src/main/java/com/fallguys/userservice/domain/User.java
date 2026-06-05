package com.fallguys.userservice.domain;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private final Long id;
    private final String keycloakId;
    private String employeeNumber;
    private String email;
    private String displayName;
    private String tenancyCode;
    private String position;
    private UserRole role;
    private UserTenancy tenancy;
    private UserStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static User create(
            String keycloakId,
            String employeeNumber,
            String email,
            String displayName,
            String tenancyCode,
            String position,
            UserRole role,
            UserTenancy tenancy
    ) {
        return new User(
                null,
                keycloakId,
                employeeNumber,
                email,
                displayName,
                tenancyCode,
                position,
                role,
                tenancy,
                UserStatus.ACTIVE,
                null,
                null
        );
    }

    public static User restore(
            Long id,
            String keycloakId,
            String employeeNumber,
            String email,
            String displayName,
            String tenancyCode,
            String position,
            UserRole role,
            UserTenancy tenancy,
            UserStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new User(
                id,
                keycloakId,
                employeeNumber,
                email,
                displayName,
                tenancyCode,
                position,
                role,
                tenancy,
                status == null ? UserStatus.ACTIVE : status,
                createdAt,
                updatedAt
        );
    }
}
