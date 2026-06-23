package com.fallguys.userservice.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserIdentityTest {

    @Test
    void stateReturnsEnabledAndPasswordNotRequiredWhenUserIsActive() {
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

        UserIdentityState state = identity.state();

        assertThat(state.enabled()).isTrue();
        assertThat(state.passwordUpdateRequired()).isFalse();
    }

    @Test
    void stateReturnsEnabledAndPasswordRequiredWhenUserIsPending() {
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

        UserIdentityState state = identity.state();

        assertThat(state.enabled()).isTrue();
        assertThat(state.passwordUpdateRequired()).isTrue();
    }

    @Test
    void stateReturnsDisabledWhenUserIsSuspended() {
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

        UserIdentityState state = identity.state();

        assertThat(state.enabled()).isFalse();
        assertThat(state.passwordUpdateRequired()).isFalse();
    }

    @Test
    void stateReturnsDisabledAndPasswordRequiredWhenSuspendedWithPendingPassword() {
        UserIdentity identity = new UserIdentity(
                "kc-004",
                "emp004",
                "emp004@erp.com",
                "이복합",
                "BR-002",
                "부산지점",
                "사원",
                UserRole.BRANCH_STAFF,
                UserTenancy.BRANCH,
                false,
                true
        );

        UserIdentityState state = identity.state();

        assertThat(state.enabled()).isFalse();
        assertThat(state.passwordUpdateRequired()).isTrue();
    }

    @Test
    void passwordUpdateRequiredFieldIsStoredInRecord() {
        UserIdentity identity = new UserIdentity(
                "kc-005",
                "emp005",
                "emp005@erp.com",
                "최패스",
                "HQ",
                "본사",
                "사원",
                UserRole.HQ_STAFF,
                UserTenancy.HQ,
                true,
                true
        );

        assertThat(identity.passwordUpdateRequired()).isTrue();
    }

    @Test
    void stateReturnsFreshInstanceEachCall() {
        UserIdentity identity = new UserIdentity(
                "kc-006",
                "emp006",
                "emp006@erp.com",
                "정멀티",
                "HQ",
                "본사",
                "대리",
                UserRole.HQ_STAFF,
                UserTenancy.HQ,
                true,
                false
        );

        UserIdentityState state1 = identity.state();
        UserIdentityState state2 = identity.state();

        assertThat(state1).isEqualTo(state2);
        assertThat(state1.enabled()).isTrue();
        assertThat(state1.passwordUpdateRequired()).isFalse();
    }
}