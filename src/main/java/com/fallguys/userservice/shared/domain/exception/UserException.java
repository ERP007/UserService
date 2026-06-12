package com.fallguys.userservice.shared.domain.exception;

public class UserException extends BusinessException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
