package com.fallguys.userservice.shared.domain.exception;

public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(UserErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    protected BusinessException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    public String getCode() {
        return code;
    }
}
