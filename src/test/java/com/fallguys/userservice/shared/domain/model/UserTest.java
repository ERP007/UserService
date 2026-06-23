package com.fallguys.userservice.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void createsUserFromActiveIdentity() {
        UserIdentity identity = new UserIdentity(
                "kc-001",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                true,
                false
        );

        User user = User.createFromIdentity(identity);

        assertThat(user.getKeycloakId()).isEqualTo("kc-001");
        assertThat(user.getEmployeeNumber()).isEqualTo("emp001");
        assertThat(user.getEmail()).isEqualTo("emp001@erp.com");
        assertThat(user.getDisplayName()).isEqualTo("홍길동");
        assertThat(user.getTenancyCode()).isEqualTo("HQ");
        assertThat(user.getTenancyName()).isEqualTo("본사");
        assertThat(user.getPosition()).isEqualTo("부장");
        assertThat(user.getRole()).isEqualTo(UserRole.HQ_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.HQ);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getId()).isNull();
    }

    @Test
    void createsUserFromPendingIdentity() {
        UserIdentity identity = new UserIdentity(
                "kc-002",
                "emp002",
                "emp002@erp.com",
                "김신입",
                "BR-001",
                "강남지점",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                true,
                true
        );

        User user = User.createFromIdentity(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getKeycloakId()).isEqualTo("kc-002");
        assertThat(user.getEmployeeNumber()).isEqualTo("emp002");
    }

    @Test
    void createsUserFromSuspendedIdentity() {
        UserIdentity identity = new UserIdentity(
                "kc-003",
                "emp003",
                "emp003@erp.com",
                "박정지",
                "HQ",
                "본사",
                "과장",
                UserRole.HQ_STAFF,
                UserTenancy.HQ,
                false,
                false
        );

        User user = User.createFromIdentity(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getKeycloakId()).isEqualTo("kc-003");
    }

    @Test
    void createsUserFromDisabledPendingIdentityAsSuspended() {
        UserIdentity identity = new UserIdentity(
                "kc-004",
                "emp004",
                "emp004@erp.com",
                "이비활",
                "BR-002",
                "부산지점",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                false,
                true
        );

        User user = User.createFromIdentity(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void createFromIdentityUsesTenancyCodeWhenTenancyNameIsBlank() {
        UserIdentity identity = new UserIdentity(
                "kc-005",
                "emp005",
                "emp005@erp.com",
                "최빈네임",
                "BR-003",
                null,
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                true,
                false
        );

        User user = User.createFromIdentity(identity);

        assertThat(user.getTenancyName()).isEqualTo("BR-003");
    }

    @Test
    void syncIdentityProfileUpdatesAllFields() {
        User user = User.create(
                "old-kc-id",
                "old-emp",
                "old@erp.com",
                "이전 이름",
                "OLD-CODE",
                "이전 지점",
                "이전 직책",
                UserRole.HQ_STAFF,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "new-kc-id",
                "new-emp",
                "new@erp.com",
                "새 이름",
                "NEW-CODE",
                "새 지점",
                "새 직책",
                UserRole.BRANCH_MANAGER,
                UserTenancy.BRANCH,
                true,
                false
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getKeycloakId()).isEqualTo("new-kc-id");
        assertThat(user.getEmployeeNumber()).isEqualTo("new-emp");
        assertThat(user.getEmail()).isEqualTo("new@erp.com");
        assertThat(user.getDisplayName()).isEqualTo("새 이름");
        assertThat(user.getTenancyCode()).isEqualTo("NEW-CODE");
        assertThat(user.getTenancyName()).isEqualTo("새 지점");
        assertThat(user.getPosition()).isEqualTo("새 직책");
        assertThat(user.getRole()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(user.getTenancy()).isEqualTo(UserTenancy.BRANCH);
    }

    @Test
    void syncIdentityProfileActivatesUserWhenEnabledAndPasswordNotRequired() {
        User user = User.createPending(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                true,
                false
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void syncIdentityProfileMarksPendingWhenEnabledAndPasswordRequired() {
        User user = User.create(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                true,
                true
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void syncIdentityProfileSuspendsUserWhenDisabled() {
        User user = User.create(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                false,
                false
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void syncIdentityProfileUsesTenancyCodeWhenTenancyNameIsNull() {
        User user = User.create(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "NEW-CODE",
                null,
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                true,
                false
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getTenancyCode()).isEqualTo("NEW-CODE");
        assertThat(user.getTenancyName()).isEqualTo("NEW-CODE");
    }

    @Test
    void syncIdentityProfileUsesTenancyNameWhenProvided() {
        User user = User.create(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "OLD-CODE",
                "이전 지점",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );
        UserIdentity identity = new UserIdentity(
                "kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "NEW-CODE",
                "새 지점 이름",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                true,
                false
        );

        user.syncIdentityProfile(identity);

        assertThat(user.getTenancyName()).isEqualTo("새 지점 이름");
    }

    @Test
    void assignKeycloakIdSetsKeycloakIdWhenValid() {
        User user = User.create(
                null,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId("new-kc-id");

        assertThat(user.getKeycloakId()).isEqualTo("new-kc-id");
    }

    @Test
    void assignKeycloakIdTrimsWhitespace() {
        User user = User.create(
                null,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId("  trimmed-id  ");

        assertThat(user.getKeycloakId()).isEqualTo("trimmed-id");
    }

    @Test
    void assignKeycloakIdIgnoresNullInput() {
        User user = User.create(
                "existing-kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId(null);

        assertThat(user.getKeycloakId()).isEqualTo("existing-kc-id");
    }

    @Test
    void assignKeycloakIdIgnoresBlankInput() {
        User user = User.create(
                "existing-kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId("   ");

        assertThat(user.getKeycloakId()).isEqualTo("existing-kc-id");
    }

    @Test
    void assignKeycloakIdIgnoresEmptyString() {
        User user = User.create(
                "existing-kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId("");

        assertThat(user.getKeycloakId()).isEqualTo("existing-kc-id");
    }

    @Test
    void assignKeycloakIdOverwritesExistingKeycloakId() {
        User user = User.create(
                "old-kc-id",
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ
        );

        user.assignKeycloakId("new-kc-id");

        assertThat(user.getKeycloakId()).isEqualTo("new-kc-id");
    }
}