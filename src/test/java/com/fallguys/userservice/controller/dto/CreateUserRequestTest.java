package com.fallguys.userservice.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.domain.CreateUserCommand;
import com.fallguys.userservice.domain.PasswordIssueMode;
import com.fallguys.userservice.domain.UserRole;
import com.fallguys.userservice.domain.UserTenancy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
