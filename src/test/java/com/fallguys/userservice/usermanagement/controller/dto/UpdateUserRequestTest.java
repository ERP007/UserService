package com.fallguys.userservice.usermanagement.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.usermanagement.domain.UpdateUserCommand;
import org.junit.jupiter.api.Test;

class UpdateUserRequestTest {

    @Test
    void fallsBackToRoleTenancyWhenTenancyIsBlank() {
        UpdateUserRequest request = new UpdateUserRequest(
                "branch001@erp.com",
                "지점 담당자",
                "BR-SE-001",
                "서울 1창고",
                "부장",
                "BRANCH_MANAGER",
                " "
        );

        UpdateUserCommand command = request.toCommand("keycloak-id");

        assertThat(command.role()).isEqualTo(UserRole.BRANCH_MANAGER);
        assertThat(command.tenancy()).isEqualTo(UserTenancy.BRANCH);
    }

    @Test
    void rejectsUnsupportedTenancyWhenTenancyIsNotBlank() {
        UpdateUserRequest request = new UpdateUserRequest(
                "branch001@erp.com",
                "지점 담당자",
                "BR-SE-001",
                "서울 1창고",
                "부장",
                "BRANCH_MANAGER",
                "UNKNOWN"
        );

        assertThatThrownBy(() -> request.toCommand("keycloak-id"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_REQUEST);
    }
}
