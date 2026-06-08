package com.fallguys.userservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private UserIdentityManager userIdentityManager;

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

    @Test
    void findsUsersWhenAccessTokenClaimsAreAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();
        UserListPage expected = new UserListPage(List.of(), 1, 10, 0, 0, false, false);
        when(userRepository.findUsers(query)).thenReturn(expected);

        UserListPage actual = userService.findUsers(jwt, query);

        assertThat(actual).isSameAs(expected);
        verify(userRepository).findUsers(query);
    }

    @Test
    void rejectsUserListAccessWhenUserRoleIsNotAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "HQ_MANAGER", "부장");
        UserSearchQuery query = userSearchQuery();

        assertThatThrownBy(() -> userService.findUsers(jwt, query))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findUsers(any(UserSearchQuery.class));
    }

    @Test
    void rejectsUserListAccessWhenTenancyTypeIsNotAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "HQ", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();

        assertThatThrownBy(() -> userService.findUsers(jwt, query))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findUsers(any(UserSearchQuery.class));
    }

    @Test
    void rejectsUserListAccessWhenTenancyCodeIsNotAdmin() {
        Jwt jwt = jwt("admin001", "HQ", "ADMIN", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();

        assertThatThrownBy(() -> userService.findUsers(jwt, query))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findUsers(any(UserSearchQuery.class));
    }

    @Test
    void createsUserInKeycloakAndLocalDatabaseWhenAdminRequests() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        CreateUserCommand command = createUserCommand();
        UserIdentity identity = new UserIdentity(
                "created-keycloak-id",
                command.employeeNumber(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                command.tenancy(),
                true
        );
        when(userIdentityManager.create(command)).thenReturn(identity);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUserResult result = userService.createUser(jwt, command);

        assertThat(result.temporaryPassword()).isNull();
        assertThat(result.user().getKeycloakId()).isEqualTo("created-keycloak-id");
        assertThat(result.user().getEmployeeNumber()).isEqualTo("branch001");
        assertThat(result.user().getEmail()).isEqualTo("branch001@erp.com");
        assertThat(result.user().getDisplayName()).isEqualTo("지점 담당자");
        assertThat(result.user().getTenancyCode()).isEqualTo("BR-001");
        assertThat(result.user().getRole()).isEqualTo(UserRole.BRANCH_STAFF);
        assertThat(result.user().getTenancy()).isEqualTo(UserTenancy.BRANCH);
        assertThat(result.user().getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userIdentityManager).create(command);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createsUserWithAutoGeneratedTemporaryPassword() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        CreateUserCommand command = autoPasswordCreateUserCommand();
        when(userIdentityManager.create(any(CreateUserCommand.class)))
                .thenAnswer(invocation -> {
                    CreateUserCommand issuedCommand = invocation.getArgument(0);
                    return new UserIdentity(
                            "created-keycloak-id",
                            issuedCommand.employeeNumber(),
                            issuedCommand.email(),
                            issuedCommand.displayName(),
                            issuedCommand.tenancyCode(),
                            issuedCommand.position(),
                            issuedCommand.role(),
                            issuedCommand.tenancy(),
                            true
                    );
                });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUserResult result = userService.createUser(jwt, command);

        ArgumentCaptor<CreateUserCommand> commandCaptor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(userIdentityManager).create(commandCaptor.capture());
        String generatedPassword = commandCaptor.getValue().initialPassword();
        assertThat(generatedPassword)
                .isNotBlank()
                .hasSizeGreaterThanOrEqualTo(TemporaryPasswordPolicy.MIN_LENGTH)
                .matches(".*[A-Za-z].*")
                .matches(".*\\d.*");
        assertThat(result.temporaryPassword()).isEqualTo(generatedPassword);
        assertThat(result.user().getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsCreateUserWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertThatThrownBy(() -> userService.createUser(jwt, createUserCommand()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).save(any(User.class));
    }

    private UserSearchQuery userSearchQuery() {
        return new UserSearchQuery(
                1,
                10,
                null,
                null,
                null,
                null,
                UserSortBy.EMPLOYEE_NO,
                UserSortDirection.ASC
        );
    }

    private CreateUserCommand createUserCommand() {
        return new CreateUserCommand(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                PasswordIssueMode.MANUAL,
                "Temp1234!"
        );
    }

    private CreateUserCommand autoPasswordCreateUserCommand() {
        return new CreateUserCommand(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                PasswordIssueMode.AUTO,
                null
        );
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
