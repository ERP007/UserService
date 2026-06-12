package com.fallguys.userservice.shared.domain.exception;

public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String code;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    protected BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return code;
    }
}
