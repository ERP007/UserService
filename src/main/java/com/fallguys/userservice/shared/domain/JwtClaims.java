package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaims {

    private static final String ADMIN_TENANCY_CODE = "ADMIN";

    private JwtClaims() {
    }

    public static void requireAdmin(Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        UserRole role = role(authenticatedJwt);
        UserTenancy tenancy = tenancy(authenticatedJwt);
        String tenancyCode = authenticatedJwt.getClaimAsString("tenancy_code");

        if (!ADMIN_TENANCY_CODE.equals(tenancyCode) || role != UserRole.ADMIN || tenancy != UserTenancy.ADMIN) {
            throw new UserException(UserErrorCode.USER_ADMIN_REQUIRED);
        }
    }

    public static UserRole role(Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserRole.fromClaim(authenticatedJwt.getClaimAsString("user_role"))
                .orElseThrow(() -> new UserException(UserErrorCode.USER_INVALID_TOKEN_CLAIM));
    }

    public static UserTenancy tenancy(Jwt jwt) {
        Jwt authenticatedJwt = requireJwt(jwt);
        return UserTenancy.fromClaim(authenticatedJwt.getClaimAsString("tenancy_type"))
                .orElseThrow(() -> new UserException(UserErrorCode.USER_INVALID_TOKEN_CLAIM));
    }

    private static Jwt requireJwt(Jwt jwt) {
        if (jwt == null) {
            throw new UserException(UserErrorCode.USER_INVALID_TOKEN_CLAIM);
        }

        return jwt;
    }
}
