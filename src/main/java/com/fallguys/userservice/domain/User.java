package com.fallguys.userservice.domain;

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
                UserStatus.ACTIVE
        );
    }

    public static User createPending(
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
                UserStatus.PENDING
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
            UserStatus status
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
                status == null ? UserStatus.ACTIVE : status
        );
    }

    public void updateSessionClaims(
            String employeeNumber,
            String email,
            String displayName,
            String tenancyCode,
            String position,
            UserRole role,
            UserTenancy tenancy
    ) {
        this.employeeNumber = employeeNumber;
        this.email = email;
        this.displayName = displayName;
        this.tenancyCode = tenancyCode;
        this.position = position;
        this.role = role;
        this.tenancy = tenancy;
        this.status = UserStatus.ACTIVE;
    }

    public void markPasswordResetRequired() {
        this.status = UserStatus.PENDING;
    }
}
