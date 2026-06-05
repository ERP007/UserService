package com.fallguys.userservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String KEYCLOAK_ID = "7ded38db-833c-47fd-862d-76e32d3a4935";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createsActiveUserFromRelayedKeycloakAccessToken() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.getOrCreateUser(jwt);

        assertThat(user.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(user.getEmployeeNumber()).isEqualTo("admin001");
        assertThat(user.getEmail()).isEqualTo("admin001@erp.com");
        assertThat(user.getDisplayName()).isEqualTo("윤 영선");
        assertThat(user.getTenancyCode()).isEqualTo("ADMIN");
        assertThat(user.getPosition()).isEqualTo("관리자");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.ADMIN);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void refreshesExistingUserClaimsWithoutCreatingNewUser() {
        User existing = User.create(
                KEYCLOAK_ID,
                "old-admin",
                "old-admin@erp.com",
                "Old Admin",
                "HQ",
                "과장",
                UserRole.HQ_STAFF,
                UserTenancy.HQ
        );
        Jwt jwt = jwt("admin001", "WH-HQ-001", "HQ", "HQ_MANAGER", "부장");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.getOrCreateUser(jwt);

        assertThat(user).isSameAs(existing);
        assertThat(user.getEmployeeNumber()).isEqualTo("admin001");
        assertThat(user.getTenancyCode()).isEqualTo("WH-HQ-001");
        assertThat(user.getPosition()).isEqualTo("부장");
        assertThat(user.getRole()).isEqualTo(UserRole.HQ_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.HQ);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsMissingSupportedUserRole() {
        Jwt jwt = jwt("admin001", "HQ", "HQ", "UNKNOWN", "과장");

        assertThatThrownBy(() -> userService.getOrCreateUser(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Jwt jwt(String employeeNo, String tenancyCode, String tenancyType, String userRole, String position) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(KEYCLOAK_ID)
                .issuedAt(Instant.parse("2026-06-03T00:00:00Z"))
                .expiresAt(Instant.parse("2026-06-03T01:00:00Z"))
                .claim("typ", "Bearer")
                .claim("azp", "erp-client")
                .claim("preferred_username", "admin001")
                .claim("employee_no", employeeNo)
                .claim("tenancy_code", tenancyCode)
                .claim("tenancy_type", tenancyType)
                .claim("user_role", userRole)
                .claim("position", position)
                .claim("email", "admin001@erp.com")
                .claim("name", "윤 영선")
                .build();
    }
}
