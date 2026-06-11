package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class JwtClaims {

    private static final String ADMIN_TENANCY_CODE = "ADMIN";

    private JwtClaims() {
    }

    public static void requireAdmin(Jwt jwt) {
        UserRole role = role(jwt);
        UserTenancy tenancy = tenancy(jwt);
        String tenancyCode = jwt.getClaimAsString("tenancy_code");

        if (!ADMIN_TENANCY_CODE.equals(tenancyCode) || role != UserRole.ADMIN || tenancy != UserTenancy.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin users can access this API");
        }
    }

    public static UserRole role(Jwt jwt) {
        return UserRole.fromClaim(jwt.getClaimAsString("user_role"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "JWT user_role claim is missing or unsupported"
                ));
    }

    public static UserTenancy tenancy(Jwt jwt) {
        return UserTenancy.fromClaim(jwt.getClaimAsString("tenancy_type"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "JWT tenancy_type claim is missing or unsupported"
                ));
    }
}
