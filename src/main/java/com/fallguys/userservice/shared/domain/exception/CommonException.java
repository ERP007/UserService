package com.fallguys.userservice.shared.domain.exception;

public class CommonException extends BusinessException {

    public CommonException(CommonErrorCode errorCode) {
        super(errorCode);
    }

    public CommonException(CommonErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
