package com.fallguys.userservice.usermanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fallguys.userservice.mypage.domain.MyPageRepository;
import com.fallguys.userservice.mypage.domain.MyPageService;
import com.fallguys.userservice.shared.domain.InternalUserRepository;
import com.fallguys.userservice.shared.domain.InternalUserService;
import com.fallguys.userservice.shared.domain.SessionRepository;
import com.fallguys.userservice.shared.domain.SessionService;
import com.fallguys.userservice.shared.domain.TenancyRepository;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.domain.command.CreateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.command.TemporaryPasswordPolicy;
import com.fallguys.userservice.shared.domain.command.UpdateUserIdentityCommand;
import com.fallguys.userservice.shared.domain.exception.UserAccessBlockedException;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.Tenancy;
import com.fallguys.userservice.shared.domain.model.TenancyType;
import com.fallguys.userservice.shared.domain.model.User;
import com.fallguys.userservice.shared.domain.model.UserIdentity;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserStatus;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.shared.domain.query.BatchUser;
import com.fallguys.userservice.shared.domain.query.BatchUserListResult;
import com.fallguys.userservice.shared.domain.query.UserDetail;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final String KEYCLOAK_ID = "7ded38db-833c-47fd-862d-76e32d3a4935";
    private static final Instant LOGIN_AT = Instant.parse("2026-06-03T00:00:00Z");
    private static final String LOGIN_SESSION_ID = "session-001";
    private static final Instant PASSWORD_CHANGED_AT = Instant.parse("2026-06-03T00:10:00Z");

    @Mock
    private TestUserRepository userRepository;

    @Mock
    private TenancyRepository tenancyRepository;

    @Mock
    private UserIdentityManager userIdentityManager;

    private SessionService sessionService;

    private MyPageService myPageService;

    private UserManagementService userManagementService;

    private InternalUserService internalUserService;

    private interface TestUserRepository
            extends SessionRepository, MyPageRepository, UserManagementRepository, InternalUserRepository {
    }

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(userRepository, userIdentityManager);
        myPageService = new MyPageService(userRepository, userIdentityManager, sessionService);
        userManagementService = new UserManagementService(userRepository, tenancyRepository, userIdentityManager);
        internalUserService = new InternalUserService(userRepository);
    }

    private void assertUserError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            UserErrorCode errorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Test
    void createsActiveUserFromRelayedKeycloakAccessToken() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = sessionService.synchronizeSession(jwt);

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

        User user = sessionService.synchronizeSession(jwt);

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

        User user = sessionService.synchronizeSession(jwt);

        assertThat(user).isSameAs(existing);
        verify(userIdentityManager, never()).findPasswordChangedAt(any(String.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsMissingSupportedUserRole() {
        Jwt jwt = jwt("admin001", "HQ", "HQ", "UNKNOWN", "과장");

        assertUserError(
                () -> sessionService.synchronizeSession(jwt),
                UserErrorCode.USER_INVALID_TOKEN_CLAIM
        );
    }

    @Test
    void findsUsersWhenAccessTokenClaimsAreAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();
        UserListPage expected = new UserListPage(List.of(), 1, 10, 0, 0, false, false);
        when(userRepository.findUsers(query)).thenReturn(expected);

        UserListPage actual = userManagementService.findUsers(jwt, query);

        assertThat(actual).isSameAs(expected);
        verify(userRepository).findUsers(query);
    }

    @Test
    void rejectsUserListAccessWhenUserRoleIsNotAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "HQ_MANAGER", "부장");
        UserSearchQuery query = userSearchQuery();

        assertUserError(
                () -> userManagementService.findUsers(jwt, query),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
        verify(userRepository, never()).findUsers(any(UserSearchQuery.class));
    }

    @Test
    void rejectsUserListAccessWhenTenancyTypeIsNotAdmin() {
        Jwt jwt = jwt("admin001", "ADMIN", "HQ", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();

        assertUserError(
                () -> userManagementService.findUsers(jwt, query),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
        verify(userRepository, never()).findUsers(any(UserSearchQuery.class));
    }

    @Test
    void rejectsUserListAccessWhenTenancyCodeIsNotAdmin() {
        Jwt jwt = jwt("admin001", "HQ", "ADMIN", "ADMIN", "관리자");
        UserSearchQuery query = userSearchQuery();

        assertUserError(
                () -> userManagementService.findUsers(jwt, query),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
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

        BatchUserListResult result = internalUserService.findBatchUsers(employeeNumbers);

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
        assertUserError(
                () -> internalUserService.findBatchUsers(List.of(" ", "")),
                UserErrorCode.USER_EMPLOYEE_NUMBERS_REQUIRED
        );
        verify(userRepository, never()).findBatchUsersByEmployeeNumbers(any());
    }

    @Test
    void rejectsBatchUserListWhenEmployeeNumbersExceedMaxSize() {
        List<String> employeeNumbers = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(number -> "EMP%03d".formatted(number))
                .toList();

        assertUserError(
                () -> internalUserService.findBatchUsers(employeeNumbers),
                UserErrorCode.USER_BATCH_SIZE_EXCEEDED
        );
        verify(userRepository, never()).findBatchUsersByEmployeeNumbers(any());
    }

    @Test
    void findsInternalUserByEmployeeNumberIgnoringCase() {
        when(userRepository.findBatchUsersByEmployeeNumbers(List.of("emp001")))
                .thenReturn(List.of(new BatchUser("emp001", "김철수", "대리")));

        BatchUser result = internalUserService.findByEmployeeNum(" EMP001 ");

        assertThat(result.employeeNumber()).isEqualTo("emp001");
        assertThat(result.name()).isEqualTo("김철수");
        assertThat(result.position()).isEqualTo("대리");
        verify(userRepository).findBatchUsersByEmployeeNumbers(List.of("emp001"));
    }

    @Test
    void rejectsInternalUserWhenEmployeeNumberDoesNotExist() {
        when(userRepository.findBatchUsersByEmployeeNumbers(List.of("missing")))
                .thenReturn(List.of());

        assertUserError(
                () -> internalUserService.findByEmployeeNum("missing"),
                UserErrorCode.USER_NOT_FOUND
        );
        verify(userRepository).findBatchUsersByEmployeeNumbers(List.of("missing"));
    }

    @Test
    void rejectsInternalUserWhenEmployeeNumberIsNullOrBlank() {
        assertUserError(
                () -> internalUserService.findByEmployeeNum(null),
                UserErrorCode.USER_EMPLOYEE_NUMBER_REQUIRED
        );
        assertUserError(
                () -> internalUserService.findByEmployeeNum(" "),
                UserErrorCode.USER_EMPLOYEE_NUMBER_REQUIRED
        );
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

        UserDetail actual = userManagementService.findUserDetail(jwt, targetKeycloakId);

        assertThat(actual).isSameAs(expected);
        verify(userRepository).findDetailByKeycloakId(targetKeycloakId);
    }

    @Test
    void rejectsUserDetailAccessWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertUserError(
                () -> userManagementService.findUserDetail(jwt, "target-keycloak-id"),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
        verify(userRepository, never()).findDetailByKeycloakId(any(String.class));
    }

    @Test
    void rejectsUserDetailWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findDetailByKeycloakId("missing-keycloak-id")).thenReturn(Optional.empty());

        assertUserError(
                () -> userManagementService.findUserDetail(jwt, "missing-keycloak-id"),
                UserErrorCode.USER_NOT_FOUND
        );
    }

    @Test
    void findsMyPageByAuthenticatedUserSubject() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        User user = User.create(
                KEYCLOAK_ID,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "점장",
                UserRole.BRANCH_MANAGER,
                UserTenancy.BRANCH
        );
        UserDetail expected = userDetail(KEYCLOAK_ID, UserStatus.ACTIVE);
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userIdentityManager.findState(KEYCLOAK_ID)).thenReturn(new UserIdentityState(true, false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(expected));

        UserDetail actual = myPageService.findMyPage(jwt);

        assertThat(actual).isSameAs(expected);
        assertThat(user.getLastLoginAt()).isEqualTo(LOGIN_AT);
        assertThat(user.getLastLoginSessionId()).isEqualTo(LOGIN_SESSION_ID);
        assertThat(user.getPasswordChangedAt()).isEqualTo(PASSWORD_CHANGED_AT);
        verify(userIdentityManager).findPasswordChangedAt(KEYCLOAK_ID);
        verify(userIdentityManager).findState(KEYCLOAK_ID);
        verify(userRepository).save(user);
        verify(userRepository).findDetailByKeycloakId(KEYCLOAK_ID);
    }

    @Test
    void rejectsMyPageWhenUserDoesNotExist() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userIdentityManager.findState(KEYCLOAK_ID)).thenReturn(new UserIdentityState(true, false));
        when(userRepository.findDetailByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());

        assertUserError(
                () -> myPageService.findMyPage(jwt),
                UserErrorCode.USER_NOT_FOUND
        );
    }

    @Test
    void rejectsMyPageWhenUserIsPending() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        User user = User.createPending(
                KEYCLOAK_ID,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "점장",
                UserRole.BRANCH_MANAGER,
                UserTenancy.BRANCH
        );
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userIdentityManager.findState(KEYCLOAK_ID)).thenReturn(new UserIdentityState(true, true));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertUserError(
                () -> myPageService.findMyPage(jwt),
                UserErrorCode.USER_MYPAGE_PASSWORD_CHANGE_REQUIRED
        );
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userRepository, never()).findDetailByKeycloakId(any(String.class));
    }

    @Test
    void rejectsMyPageWhenUserIsSuspended() {
        Jwt jwt = jwt("branch001", "WH-BR-001", "BRANCH", "BRANCH_MANAGER", "점장");
        User user = User.create(
                KEYCLOAK_ID,
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "점장",
                UserRole.BRANCH_MANAGER,
                UserTenancy.BRANCH
        );
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
        when(userIdentityManager.findPasswordChangedAt(KEYCLOAK_ID)).thenReturn(Optional.of(PASSWORD_CHANGED_AT));
        when(userIdentityManager.findState(KEYCLOAK_ID)).thenReturn(new UserIdentityState(false, false));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertUserError(
                () -> myPageService.findMyPage(jwt),
                UserErrorCode.USER_SUSPENDED
        );
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository, never()).findDetailByKeycloakId(any(String.class));
    }

    @Test
    void keepsMyPageSynchronizationWhenAccessDenied() throws NoSuchMethodException {
        Method method = MyPageService.class.getDeclaredMethod("findMyPage", Jwt.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional.noRollbackFor())
                .contains(UserAccessBlockedException.class)
                .doesNotContain(UserException.class);
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

        UserDetail result = userManagementService.updateUser(jwt, command);

        assertThat(result).isSameAs(detail);
        assertThat(user.getEmail()).isEqualTo("updated@erp.com");
        assertThat(user.getDisplayName()).isEqualTo("수정 사용자");
        assertThat(user.getTenancyCode()).isEqualTo("WH-BR-001");
        assertThat(user.getPosition()).isEqualTo("MANAGER");
        assertThat(user.getRole()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.BRANCH);
        verify(userIdentityManager).update(new UpdateUserIdentityCommand(
                command.keycloakId(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                UserTenancy.BRANCH
        ));
        verify(userRepository).save(user);
    }

    @Test
    void rejectsUpdateUserWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertUserError(
                () -> userManagementService.updateUser(jwt, updateUserCommand()),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
        verify(userRepository, never()).findByKeycloakId(any(String.class));
        verifyNoInteractions(tenancyRepository);
        verifyNoInteractions(userIdentityManager);
    }

    @Test
    void rejectsUpdateUserWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        UpdateUserCommand command = updateUserCommand();
        when(userRepository.findByKeycloakId(command.keycloakId())).thenReturn(Optional.empty());

        assertUserError(
                () -> userManagementService.updateUser(jwt, command),
                UserErrorCode.USER_NOT_FOUND
        );
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

        assertUserError(
                () -> userManagementService.updateUser(jwt, command),
                UserErrorCode.USER_TENANCY_NOT_FOUND
        );
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

        assertThatThrownBy(() -> userManagementService.updateUser(jwt, command))
                .isSameAs(failure);
        verify(userIdentityManager, never()).update(any(UpdateUserIdentityCommand.class));
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

        CreateUserResult result = userManagementService.createUser(jwt, command);

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

        CreateUserResult result = userManagementService.createUser(jwt, command);

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

        assertUserError(
                () -> userManagementService.createUser(jwt, command),
                UserErrorCode.USER_TENANCY_MISMATCH
        );
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsCreateUserWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertUserError(
                () -> userManagementService.createUser(jwt, createUserCommand()),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
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

        ResetPasswordResult result = userManagementService.resetPassword(jwt, targetKeycloakId);

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

        assertUserError(
                () -> userManagementService.resetPassword(jwt, "target-keycloak-id"),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
        verifyNoInteractions(userIdentityManager);
        verify(userRepository, never()).findByKeycloakId(any(String.class));
    }

    @Test
    void rejectsResetPasswordWhenUserDoesNotExist() {
        Jwt jwt = jwt("admin001", "ADMIN", "ADMIN", "ADMIN", "관리자");
        when(userRepository.findByKeycloakId("missing-keycloak-id")).thenReturn(Optional.empty());

        assertUserError(
                () -> userManagementService.resetPassword(jwt, "missing-keycloak-id"),
                UserErrorCode.USER_NOT_FOUND
        );
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

        assertThatThrownBy(() -> userManagementService.resetPassword(jwt, targetKeycloakId))
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

        User result = userManagementService.toggleSuspension(jwt, targetKeycloakId);

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

        User result = userManagementService.toggleSuspension(jwt, targetKeycloakId);

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

        User result = userManagementService.toggleSuspension(jwt, targetKeycloakId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        verify(userIdentityManager).findState(targetKeycloakId);
        verify(userIdentityManager).updateEnabled(targetKeycloakId, true);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsToggleSuspensionWhenRequesterIsNotAdmin() {
        Jwt jwt = jwt("branch001", "BR-001", "BRANCH", "BRANCH_MANAGER", "점장");

        assertUserError(
                () -> userManagementService.toggleSuspension(jwt, "target-keycloak-id"),
                UserErrorCode.USER_ADMIN_REQUIRED
        );
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

        assertThatThrownBy(() -> userManagementService.toggleSuspension(jwt, targetKeycloakId))
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
