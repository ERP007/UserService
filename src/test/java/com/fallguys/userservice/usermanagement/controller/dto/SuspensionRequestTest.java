package com.fallguys.userservice.usermanagement.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import org.junit.jupiter.api.Test;

class SuspensionRequestTest {

    @Test
    void returnsRequestedSuspensionState() {
        SuspensionRequest request = new SuspensionRequest(true);

        assertThat(request.requiredSuspended()).isTrue();
    }

    @Test
    void rejectsMissingSuspensionState() {
        SuspensionRequest request = new SuspensionRequest(null);

        assertThatThrownBy(request::requiredSuspended)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_INVALID_REQUEST);
    }
}
