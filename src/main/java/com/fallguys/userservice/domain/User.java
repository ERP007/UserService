package com.fallguys.userservice.domain;

import java.time.Instant;
import java.util.Objects;

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
    private Instant lastLoginAt;
    private String lastLoginSessionId;
    private Instant passwordChangedAt;

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
                null,
                null
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
                UserStatus.PENDING,
                null,
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
            Instant lastLoginAt,
            String lastLoginSessionId,
            Instant passwordChangedAt
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
                lastLoginAt,
                lastLoginSessionId,
                passwordChangedAt
        );
    }

    public boolean updateSessionClaims(
            String employeeNumber,
            String email,
            String displayName,
            String tenancyCode,
            String position,
            UserRole role,
            UserTenancy tenancy
    ) {
        boolean changed = !Objects.equals(this.employeeNumber, employeeNumber)
                || !Objects.equals(this.email, email)
                || !Objects.equals(this.displayName, displayName)
                || !Objects.equals(this.tenancyCode, tenancyCode)
                || !Objects.equals(this.position, position)
                || this.role != role
                || this.tenancy != tenancy
                || this.status != UserStatus.ACTIVE;

        this.employeeNumber = employeeNumber;
        this.email = email;
        this.displayName = displayName;
        this.tenancyCode = tenancyCode;
        this.position = position;
        this.role = role;
        this.tenancy = tenancy;
        this.status = UserStatus.ACTIVE;

        return changed;
    }

    public boolean updateLastLogin(Instant loginAt, String sessionId) {
        if (loginAt == null) {
            return false;
        }

        if (hasText(sessionId) && Objects.equals(lastLoginSessionId, sessionId) && lastLoginAt != null) {
            return false;
        }

        if (!hasText(sessionId) && Objects.equals(lastLoginAt, loginAt)) {
            return false;
        }

        lastLoginAt = loginAt;
        lastLoginSessionId = hasText(sessionId) ? sessionId : lastLoginSessionId;
        return true;
    }

    public boolean updatePasswordChangedAt(Instant passwordChangedAt) {
        if (passwordChangedAt == null || Objects.equals(this.passwordChangedAt, passwordChangedAt)) {
            return false;
        }

        this.passwordChangedAt = passwordChangedAt;
        return true;
    }

    public void markPasswordResetRequired() {
        this.status = UserStatus.PENDING;
    }

    public void applyIdentityState(UserIdentityState identityState) {
        if (!identityState.enabled()) {
            this.status = UserStatus.SUSPENDED;
            return;
        }

        this.status = identityState.passwordUpdateRequired()
                ? UserStatus.PENDING
                : UserStatus.ACTIVE;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
