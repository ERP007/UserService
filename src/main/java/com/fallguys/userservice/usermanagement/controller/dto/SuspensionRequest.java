package com.fallguys.userservice.usermanagement.controller.dto;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;

public record SuspensionRequest(
        Boolean suspended
) {

    public boolean requiredSuspended() {
        if (suspended == null) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return suspended;
    }
}
