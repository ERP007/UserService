package com.fallguys.userservice.shared.infrastructure.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.userservice.shared.domain.exception.CommonErrorCode;
import com.fallguys.userservice.shared.domain.model.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String USER_ROLE_CLAIM = "user_role";
    private static final String ROLE_PREFIX = "ROLE_";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/users/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/users/swagger-ui.html",
                                "/users/swagger-ui/**",
                                "/users/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, CommonErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpStatus.FORBIDDEN, CommonErrorCode.ACCESS_DENIED)))
                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .authenticationEntryPoint((request, response, ex) ->
                                        writeError(response, HttpStatus.UNAUTHORIZED, CommonErrorCode.AUTHENTICATION_REQUIRED))
                                .accessDeniedHandler((request, response, ex) ->
                                        writeError(response, HttpStatus.FORBIDDEN, CommonErrorCode.ACCESS_DENIED))
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )  // OAuth2 Resource Server로 동작하게 만드는 설정
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::convertUserRoleAuthority);
        return converter;
    }

    private Collection<GrantedAuthority> convertUserRoleAuthority(Jwt jwt) {
        return UserRole.fromClaim(jwt.getClaimAsString(USER_ROLE_CLAIM))
                .<Collection<GrantedAuthority>>map(role ->
                        List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))
                )
                .orElseGet(List::of);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            CommonErrorCode errorCode
    ) throws IOException {
        Map<String, Object> problemDetail = new LinkedHashMap<>();
        problemDetail.put("type", "about:blank");
        problemDetail.put("title", status.getReasonPhrase());
        problemDetail.put("status", status.value());
        problemDetail.put("detail", errorCode.getMessage());
        problemDetail.put("errorCode", errorCode.getCode());
        problemDetail.put("timestamp", Instant.now().toString());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
