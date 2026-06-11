package com.fallguys.userservice.shared.controller.dto;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public record SessionResponse(
        SessionContentResponse content
) {

    public static SessionResponse from(Jwt jwt) {
        String employeeNumber = jwt.getClaimAsString("employee_no");
        String name = claimOrDefault(jwt, "name", employeeNumber);

        return new SessionResponse(new SessionContentResponse(
                jwt.getClaimAsString("tenancy_code"),
                jwt.getClaimAsString("tenancy_type"),
                jwt.getClaimAsString("user_role"),
                jwt.getClaimAsString("position"),
                employeeNumber,
                name
        ));
    }

    private static String claimOrDefault(Jwt jwt, String claimName, String defaultValue) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (StringUtils.hasText(claimValue)) {
            return claimValue;
        }

        return defaultValue;
    }
}
