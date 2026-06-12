package com.fallguys.userservice.usermanagement.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.usermanagement.domain.CreateUserCommand;
import com.fallguys.userservice.usermanagement.domain.PasswordIssueMode;
import org.junit.jupiter.api.Test;

class CreateUserRequestTest {

    @Test
    void convertsAutoPasswordRequestWithoutInitialPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "사원",
                "BRANCH_STAFF",
                "BRANCH",
                "AUTO",
                null
        );

        CreateUserCommand command = request.toCommand();

        assertThat(command.employeeNumber()).isEqualTo("branch001");
        assertThat(command.tenancyCode()).isEqualTo("WH-BR-001");
        assertThat(command.role()).isEqualTo(UserRole.BRANCH_STAFF);
        assertThat(command.tenancy()).isEqualTo(UserTenancy.BRANCH);
        assertThat(command.passwordIssueMode()).isEqualTo(PasswordIssueMode.AUTO);
        assertThat(command.initialPassword()).isNull();
    }

    @Test
    void convertsManualPasswordRequest() {
        CreateUserRequest request = new CreateUserRequest(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "사원",
                "BRANCH_STAFF",
                "BRANCH",
                "MANUAL",
                "Temp1234!"
        );

        CreateUserCommand command = request.toCommand();

        assertThat(command.passwordIssueMode()).isEqualTo(PasswordIssueMode.MANUAL);
        assertThat(command.initialPassword()).isEqualTo("Temp1234!");
    }

    @Test
    void rejectsAutoPasswordRequestWithInitialPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "사원",
                "BRANCH_STAFF",
                "BRANCH",
                "AUTO",
                "Temp1234!"
        );

        assertThatThrownBy(request::toCommand)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INITIAL_PASSWORD_AUTO_NOT_ALLOWED);
    }

    @Test
    void rejectsManualPasswordThatDoesNotSatisfyPolicy() {
        CreateUserRequest request = new CreateUserRequest(
                "branch001",
                "branch001@erp.com",
                "지점 담당자",
                "WH-BR-001",
                "사원",
                "BRANCH_STAFF",
                "BRANCH",
                "MANUAL",
                "password"
        );

        assertThatThrownBy(request::toCommand)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_TEMPORARY_PASSWORD_INVALID);
    }
}
