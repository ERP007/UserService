package com.fallguys.userservice.shared.domain.exception;

public abstract class BusinessException extends RuntimeException {

    private final UserErrorCode errorCode;
    private final String code;

    protected BusinessException(UserErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    protected BusinessException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    public UserErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return code;
    }
}
