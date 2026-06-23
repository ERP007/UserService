package com.fallguys.userservice.shared.domain.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import org.junit.jupiter.api.Test;

class CreateUserIdentityCommandTest {

    @Test
    void keycloakIdIsNullWhenNullProvided() {
        CreateUserIdentityCommand command = createCommand(null);

        assertThat(command.keycloakId()).isNull();
    }

    @Test
    void keycloakIdIsNullWhenBlankStringProvided() {
        CreateUserIdentityCommand command = createCommand("   ");

        assertThat(command.keycloakId()).isNull();
    }

    @Test
    void keycloakIdIsNullWhenEmptyStringProvided() {
        CreateUserIdentityCommand command = createCommand("");

        assertThat(command.keycloakId()).isNull();
    }

    @Test
    void keycloakIdIsPreservedWhenValidValueProvided() {
        CreateUserIdentityCommand command = createCommand("some-keycloak-id");

        assertThat(command.keycloakId()).isEqualTo("some-keycloak-id");
    }

    @Test
    void keycloakIdIsTrimmedWhenWhitespacePresent() {
        CreateUserIdentityCommand command = createCommand("  trimmed-id  ");

        assertThat(command.keycloakId()).isEqualTo("trimmed-id");
    }

    @Test
    void commandCreationSucceedsWithNullKeycloakId() {
        CreateUserIdentityCommand command = createCommand(null);

        assertThat(command.employeeNumber()).isEqualTo("emp001");
        assertThat(command.email()).isEqualTo("emp001@erp.com");
        assertThat(command.displayName()).isEqualTo("홍길동");
        assertThat(command.tenancyCode()).isEqualTo("HQ");
        assertThat(command.role()).isEqualTo(UserRole.HQ_MANAGER);
        assertThat(command.tenancy()).isEqualTo(UserTenancy.HQ);
    }

    @Test
    void rejectsCommandWhenEmployeeNumberIsNull() {
        assertThatThrownBy(() -> new CreateUserIdentityCommand(
                null,
                null,
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                "Pass1234!"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_REQUEST);
    }

    @Test
    void rejectsCommandWhenEmailIsNull() {
        assertThatThrownBy(() -> new CreateUserIdentityCommand(
                null,
                "emp001",
                null,
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                "Pass1234!"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_REQUEST);
    }

    @Test
    void rejectsCommandWhenRoleIsNull() {
        assertThatThrownBy(() -> new CreateUserIdentityCommand(
                null,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                null,
                UserTenancy.HQ,
                "Pass1234!"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_REQUEST);
    }

    @Test
    void tenancyNameDefaultsToTenancyCodeWhenBlank() {
        CreateUserIdentityCommand command = new CreateUserIdentityCommand(
                null,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                null,
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                "Pass1234!"
        );

        assertThat(command.tenancyName()).isEqualTo("HQ");
    }

    @Test
    void rejectsCommandWhenPasswordIsTooShort() {
        assertThatThrownBy(() -> new CreateUserIdentityCommand(
                null,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                "P1!"
        ))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_TEMPORARY_PASSWORD_INVALID);
    }

    private CreateUserIdentityCommand createCommand(String keycloakId) {
        return new CreateUserIdentityCommand(
                keycloakId,
                "emp001",
                "emp001@erp.com",
                "홍길동",
                "HQ",
                "본사",
                "부장",
                UserRole.HQ_MANAGER,
                UserTenancy.HQ,
                "Pass1234!"
        );
    }
}