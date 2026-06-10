package com.fallguys.userservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final Instant LOGIN_AT = Instant.parse("2026-06-03T00:00:00Z");
    private static final String LOGIN_SESSION_ID = "session-001";
    private static final Instant PASSWORD_CHANGED_AT = Instant.parse("2026-06-03T00:10:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenancyRepository tenancyRepository;

    @Mock
    private UserIdentityManager userIdentityManager;

    @InjectMocks
    private UserService userService;

    @Test
    void createsActiveUserFromRelayedKeycloakAccessToken() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
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
        assertThat(user.getLastLoginAt()).isEqualTo(LOGIN_AT);
        assertThat(user.getLastLoginSessionId()).isEqualTo(LOGIN_SESSION_ID);
        assertThat(user.getPasswordChangedAt()).isEqualTo(PASSWORD_CHANGED_AT);
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
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.getOrCreateUser(jwt);

        assertThat(user).isSameAs(existing);
        assertThat(user.getEmployeeNumber()).isEqualTo("admin001");
        assertThat(user.getTenancyCode()).isEqualTo("WH-HQ-001");
        assertThat(user.getPosition()).isEqualTo("부장");
        assertThat(user.getRole()).isEqualTo(UserRole.HQ_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.HQ);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLastLoginAt()).isEqualTo(LOGIN_AT);
        assertThat(user.getLastLoginSessionId()).isEqualTo(LOGIN_SESSION_ID);
        assertThat(user.getPasswordChangedAt()).isEqualTo(PASSWORD_CHANGED_AT);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void skipsSaveWhenSessionClaimsAndLoginMetadataAreUnchanged() {
        User existing = User.create(
                KEYCLOAK_ID,
                "admin001",
                "admin001@erp.com",
                "윤 영선",
                "ADMIN",
                "관리자",
                UserRole.ADMIN,
                UserTenancy.ADMIN
        );
        existing.updateLastLogin(LOGIN_AT, LOGIN_SESSION_ID);
        existing.updatePasswordChangedAt(PASSWORD_CHANGED_AT);
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));

        User user = userService.getOrCreateUser(jwt);

        assertThat(user).isSameAs(existing);
        verify(userIdentityManager, never()).findPasswordChangedAt(any(String.class));
        verify(userRepository, never()).save(any(User.class));
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
    void findsBatchUsersInRequestOrderAndReturnsMissingEmployeeNumbers() {
        List<String> employeeNumbers = List.of(" EMP001 ", "EMP002", "EMP003", "emp001", " ");
        when(userRepository.findBatchUsersByEmployeeNumbers(List.of("emp001", "emp002", "emp003")))
                .thenReturn(List.of(
                        new BatchUser("emp002", "이영희", "과장"),
                        new BatchUser("emp001", "김철수", "대리")
                ));

        BatchUserListResult result = userService.findBatchUsers(employeeNumbers);

        assertThat(result.users())
                .extracting(BatchUser::employeeNumber)
                .containsExactly("emp001", "emp002");
        assertThat(result.users())
                .extracting(BatchUser::name)
                .containsExactly("김철수", "이영희");
        assertThat(result.notFoundEmployeeNumbers()).containsExactly("EMP003");
        verify(userRepository).findBatchUsersByEmployeeNumbers(List.of("emp001", "emp002", "emp003"));
    }

    @Test
    void rejectsBatchUserListWhenEmployeeNumbersAreBlank() {
        assertThatThrownBy(() -> userService.findBatchUsers(List.of(" ", "")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).findBatchUsersByEmployeeNumbers(any());
    }

    @Test
    void rejectsBatchUserListWhenEmployeeNumbersExceedMaxSize() {
        List<String> employeeNumbers = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(number -> "EMP%03d".formatted(number))
                .toList();

        assertThatThrownBy(() -> userService.findBatchUsers(employeeNumbers))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).findBatchUsersByEmployeeNumbers(any());
    }

    @Test
    void findsUserDetailWhenAccessTokenClaimsAreAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        UserDetail expected = new UserDetail(
                targetKeycloakId,
                "HMC0001",
                "김정수",
                "jskim@hyundaiparts.com",
                "WH-BR-001",
                "강남 1지점",
                UserRole.BRANCH_MANAGER,
                "MANAGER",
                UserStatus.ACTIVE,
                LocalDate.parse("2023-04-12"),
                LOGIN_AT,
                PASSWORD_CHANGED_AT,
                LocalDateTime.parse("2023-04-12T10:30:00")
        );
        when(userRepository.findDetailByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(expected));

        UserDetail actual = userService.findUserDetail(jwt, targetKeycloakId);

        assertThat(actual).isSameAs(expected);
        verify(userRepository).findDetailByKeycloakId(targetKeycloakId);
    }

    @Test
    void rejectsUserDetailAccessWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertThatThrownBy(() -> userService.findUserDetail(jwt, "target-keycloak-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findDetailByKeycloakId(any(String.class));
    }

    @Test
    void rejectsUserDetailWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findDetailByKeycloakId("missing-keycloak-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserDetail(jwt, "missing-keycloak-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findsMyPageByAuthenticatedUserSubject() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        UserDetail expected = userDetail(KEYCLOAK_ID, UserStatus.ACTIVE);
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(expected));

        UserDetail actual = userService.findMyPage(jwt);

        assertThat(actual).isSameAs(expected);
        verify(userRepository).findDetailByKeycloakId(KEYCLOAK_ID);
    }

    @Test
    void rejectsMyPageWhenUserDoesNotExist() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findMyPage(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsMyPageWhenUserIsPending() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.of(userDetail(KEYCLOAK_ID, UserStatus.PENDING)));

        assertThatThrownBy(() -> userService.findMyPage(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMyPageWhenUserIsSuspended() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID))
                .thenReturn(Optional.of(userDetail(KEYCLOAK_ID, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> userService.findMyPage(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updatesUserProfileInKeycloakAndLocalDatabaseWhenAdminRequests() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        UpdateUserCommand command = new UpdateUserCommand(
                targetKeycloakId,
                "updated@erp.com",
                "수정 사용자",
                "WH-BR-001",
                "MANAGER",
                UserRole.BRANCH_MANAGER
        );
        User user = User.create(
                targetKeycloakId,
                "HMC0001",
                "old@erp.com",
                "기존 사용자",
                "HQ",
                "STAFF",
                UserRole.HQ_STAFF,
                UserTenancy.HQ
        );
        UserDetail detail = new UserDetail(
                targetKeycloakId,
                "HMC0001",
                "수정 사용자",
                "updated@erp.com",
                "WH-BR-001",
                "강남 1지점",
                UserRole.BRANCH_MANAGER,
                "MANAGER",
                UserStatus.ACTIVE,
                LocalDate.parse("2023-04-12"),
                LOGIN_AT,
                PASSWORD_CHANGED_AT,
                LocalDateTime.parse("2023-04-12T10:30:00")
        );
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(tenancyRepository.findByCode("WH-BR-001"))
                .thenReturn(Optional.of(new Tenancy("WH-BR-001", "강남 1지점", TenancyType.BRANCH)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findDetailByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(detail));

        UserDetail result = userService.updateUser(jwt, command);

        assertThat(result).isSameAs(detail);
        assertThat(user.getEmail()).isEqualTo("updated@erp.com");
        assertThat(user.getDisplayName()).isEqualTo("수정 사용자");
        assertThat(user.getTenancyCode()).isEqualTo("WH-BR-001");
        assertThat(user.getPosition()).isEqualTo("MANAGER");
        assertThat(user.getRole()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.BRANCH);
        verify(userIdentityManager).update(command, UserTenancy.BRANCH);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsUpdateUserWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertThatThrownBy(() -> userService.updateUser(jwt, updateUserCommand()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findByKeycloakId(any(String.class));
        verifyNoInteractions(tenancyRepository);
        verifyNoInteractions(userIdentityManager);
    }

    @Test
    void rejectsUpdateUserWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UpdateUserCommand command = updateUserCommand();
        when(userRepository.findByKeycloakId(command.keycloakId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(jwt, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(tenancyRepository);
        verifyNoInteractions(userIdentityManager);
    }

    @Test
    void rejectsUpdateUserWhenTenancyCodeDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UpdateUserCommand command = updateUserCommand();
        User user = User.create(
                command.keycloakId(),
                "HMC0001",
                "old@erp.com",
                "기존 사용자",
                "HQ",
                "STAFF",
                UserRole.HQ_STAFF,
                UserTenancy.HQ
        );
        when(userRepository.findByKeycloakId(command.keycloakId())).thenReturn(Optional.of(user));
        when(tenancyRepository.findByCode(command.tenancyCode())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(jwt, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void doesNotUpdateKeycloakWhenUpdateUserLocalSaveFails() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UpdateUserCommand command = updateUserCommand();
        User user = User.create(
                command.keycloakId(),
                "HMC0001",
                "old@erp.com",
                "기존 사용자",
                "HQ",
                "STAFF",
                UserRole.HQ_STAFF,
                UserTenancy.HQ
        );
        RuntimeException failure = new RuntimeException("database write failed");
        when(userRepository.findByKeycloakId(command.keycloakId())).thenReturn(Optional.of(user));
        when(tenancyRepository.findByCode(command.tenancyCode()))
                .thenReturn(Optional.of(new Tenancy(command.tenancyCode(), "강남 1지점", TenancyType.BRANCH)));
        when(userRepository.save(user)).thenThrow(failure);

        assertThatThrownBy(() -> userService.updateUser(jwt, command))
                .isSameAs(failure);
        verify(userIdentityManager, never()).update(any(UpdateUserCommand.class), any(UserTenancy.class));
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
        when(userIdentityManager.create(any(CreateUserIdentityCommand.class))).thenReturn(identity);
        when(tenancyRepository.findByCode(command.tenancyCode()))
                .thenReturn(Optional.of(new Tenancy(command.tenancyCode(), "강남 1지점", TenancyType.BRANCH)));
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
        verify(userIdentityManager).create(any(CreateUserIdentityCommand.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createsUserWithAutoGeneratedTemporaryPassword() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        CreateUserCommand command = autoPasswordCreateUserCommand();
        when(tenancyRepository.findByCode(command.tenancyCode()))
                .thenReturn(Optional.of(new Tenancy(command.tenancyCode(), "강남 1지점", TenancyType.BRANCH)));
        when(userIdentityManager.create(any(CreateUserIdentityCommand.class)))
                .thenAnswer(invocation -> {
                    CreateUserIdentityCommand issuedCommand = invocation.getArgument(0);
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

        ArgumentCaptor<CreateUserIdentityCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateUserIdentityCommand.class);
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
    void rejectsCreateUserWhenTenancyCodeAndTenancyTypeDoNotMatch() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        CreateUserCommand command = createUserCommand();
        when(tenancyRepository.findByCode(command.tenancyCode()))
                .thenReturn(Optional.of(new Tenancy(command.tenancyCode(), "본사 중앙창고", TenancyType.HQ)));

        assertThatThrownBy(() -> userService.createUser(jwt, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).save(any(User.class));
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

    @Test
    void resetsPasswordWithGeneratedTemporaryPasswordAndMarksUserPending() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.create(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResetPasswordResult result = userService.resetPassword(jwt, targetKeycloakId);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userIdentityManager).resetPassword(eq(targetKeycloakId), passwordCaptor.capture());
        String temporaryPassword = passwordCaptor.getValue();
        assertThat(temporaryPassword)
                .isNotBlank()
                .hasSizeGreaterThanOrEqualTo(TemporaryPasswordPolicy.MIN_LENGTH)
                .matches(".*[A-Za-z].*")
                .matches(".*\\d.*");
        assertThat(result.temporaryPassword()).isEqualTo(temporaryPassword);
        assertThat(result.user().getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsResetPasswordWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertThatThrownBy(() -> userService.resetPassword(jwt, "target-keycloak-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).findByKeycloakId(any(String.class));
    }

    @Test
    void rejectsResetPasswordWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId("missing-keycloak-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(jwt, "missing-keycloak-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void doesNotResetKeycloakPasswordWhenLocalSaveFails() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.create(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        RuntimeException failure = new RuntimeException("database write failed");
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenThrow(failure);

        assertThatThrownBy(() -> userService.resetPassword(jwt, targetKeycloakId))
                .isSameAs(failure);
        verify(userIdentityManager, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void togglesEnabledUserToSuspended() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.create(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userIdentityManager.findState(targetKeycloakId)).thenReturn(new UserIdentityState(true, false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.toggleSuspension(jwt, targetKeycloakId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userIdentityManager).findState(targetKeycloakId);
        verify(userIdentityManager).updateEnabled(targetKeycloakId, false);
        verify(userRepository).save(user);
    }

    @Test
    void togglesSuspendedUserToActiveWhenPasswordUpdateIsNotRequired() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.create(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        user.applyIdentityState(new UserIdentityState(false, false));
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userIdentityManager.findState(targetKeycloakId)).thenReturn(new UserIdentityState(false, false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.toggleSuspension(jwt, targetKeycloakId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userIdentityManager).findState(targetKeycloakId);
        verify(userIdentityManager).updateEnabled(targetKeycloakId, true);
        verify(userRepository).save(user);
    }

    @Test
    void togglesSuspendedUserToPendingWhenPasswordUpdateIsRequired() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.createPending(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        user.applyIdentityState(new UserIdentityState(false, true));
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userIdentityManager.findState(targetKeycloakId)).thenReturn(new UserIdentityState(false, true));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.toggleSuspension(jwt, targetKeycloakId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userIdentityManager).findState(targetKeycloakId);
        verify(userIdentityManager).updateEnabled(targetKeycloakId, true);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsToggleSuspensionWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertThatThrownBy(() -> userService.toggleSuspension(jwt, "target-keycloak-id"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).findByKeycloakId(any(String.class));
    }

    @Test
    void doesNotUpdateKeycloakEnabledWhenLocalSaveFails() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        String targetKeycloakId = "target-keycloak-id";
        User user = User.create(
                targetKeycloakId,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "BR-001",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH
        );
        RuntimeException failure = new RuntimeException("database write failed");
        when(userRepository.findByKeycloakId(targetKeycloakId)).thenReturn(Optional.of(user));
        when(userIdentityManager.findState(targetKeycloakId)).thenReturn(new UserIdentityState(true, false));
        when(userRepository.save(user)).thenThrow(failure);

        assertThatThrownBy(() -> userService.toggleSuspension(jwt, targetKeycloakId))
                .isSameAs(failure);
        verify(userIdentityManager).findState(targetKeycloakId);
        verify(userIdentityManager, never()).updateEnabled(any(String.class), anyBoolean());
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

    private UpdateUserCommand updateUserCommand() {
        return new UpdateUserCommand(
                "target-keycloak-id",
                "updated@erp.com",
                "수정 사용자",
                "WH-BR-001",
                "MANAGER",
                UserRole.BRANCH_MANAGER
        );
    }

    private UserDetail userDetail(String keycloakId, UserStatus status) {
        return new UserDetail(
                keycloakId,
                "HMC0001",
                "김정수",
                "jskim@hyundaiparts.com",
                "WH-BR-001",
                "강남 1지점",
                UserRole.BRANCH_MANAGER,
                "MANAGER",
                status,
                LocalDate.parse("2023-04-12"),
                LOGIN_AT,
                PASSWORD_CHANGED_AT,
                LocalDateTime.parse("2023-04-12T10:30:00")
        );
    }

    private Jwt jwt(String employeeNo, String tenancyCode, String tenancyType, String userRole, String position) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(KEYCLOAK_ID)
                .issuedAt(Instant.parse("2026-06-03T00:05:00Z"))
                .expiresAt(Instant.parse("2026-06-03T01:00:00Z"))
                .claim("typ", "Bearer")
                .claim("azp", "erp-client")
                .claim("auth_time", LOGIN_AT.getEpochSecond())
                .claim("sid", LOGIN_SESSION_ID)
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
