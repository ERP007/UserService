package com.fallguys.userservice.domain.exception;

public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException() {
        super(UserErrorCode.USER_ALREADY_EXISTS);
    }
}
