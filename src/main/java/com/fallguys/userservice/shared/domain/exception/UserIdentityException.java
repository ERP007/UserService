package com.fallguys.userservice.shared.domain.exception;

public class UserIdentityException extends BusinessException {

    public UserIdentityException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserIdentityException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
