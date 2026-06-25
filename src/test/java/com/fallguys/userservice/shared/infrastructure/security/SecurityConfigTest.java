package com.fallguys.userservice.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigTest {

    @Test
    void convertsUserRoleClaimToRoleAuthority() {
        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

        var authentication = converter.convert(jwtWithUserRole("BRANCH_MANAGER"));

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_BRANCH_MANAGER");
    }

    @Test
    void ignoresUnsupportedUserRoleClaim() {
        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

        var authentication = converter.convert(jwtWithUserRole("UNKNOWN"));

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    private Jwt jwtWithUserRole(String userRole) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("e25525f3-cd6b-43db-ac52-a5e0f6d4afb8")
                .claim("user_role", userRole)
                .build();
    }
}
