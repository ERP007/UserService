package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

record SessionClaims(
        String keycloakId,
        String employeeNumber,
        String email,
        String displayName,
        String tenancyCode,
        String position,
        UserRole role,
        UserTenancy tenancy
) {

    static SessionClaims from(Jwt jwt, UserRole role, UserTenancy tenancy) {
        String keycloakId = jwt.getSubject();
        String employeeNumber = requiredClaim(jwt, "employee_no");
        String email = jwt.getClaimAsString("email");
        String name = claimOrDefault(jwt, "name", employeeNumber);
        String tenancyCode = requiredClaim(jwt, "tenancy_code");
        String position = jwt.getClaimAsString("position");

        return new SessionClaims(keycloakId, employeeNumber, email, name, tenancyCode, position, role, tenancy);
    }

    private static String claimOrDefault(Jwt jwt, String claimName, String defaultValue) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (StringUtils.hasText(claimValue)) {
            return claimValue;
        }

        return defaultValue;
    }

    private static String requiredClaim(Jwt jwt, String claimName) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (StringUtils.hasText(claimValue)) {
            return claimValue;
        }

        throw new UserException(UserErrorCode.USER_INVALID_TOKEN_CLAIM);
    }
}
