package com.fallguys.userservice.shared.domain.exception;

public class UserAccessBlockedException extends UserException {

    public UserAccessBlockedException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
