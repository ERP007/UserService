package com.fallguys.userservice.shared.controller;

import java.time.Instant;

import com.fallguys.userservice.shared.domain.exception.BusinessException;
import com.fallguys.userservice.shared.domain.exception.UserAlreadyExistsException;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserIdentityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserIdentityException.class)
    public ProblemDetail handleUserIdentity(UserIdentityException ex) {
        log.error("User identity exception. code={}", ex.getCode(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists. code={}", ex.getCode());
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("User business exception. code={}", ex.getCode());
        return build(statusOf(ex.getErrorCode()), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid user request. message={}", ex.getMessage());
        UserErrorCode errorCode = UserErrorCode.USER_INVALID_REQUEST;
        return build(HttpStatus.BAD_REQUEST, errorCode.getCode(), errorCode.getMessage());
    }

    private ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        return problemDetail;
    }

    private HttpStatus statusOf(UserErrorCode errorCode) {
        return switch (errorCode) {
            case USER_AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case USER_ADMIN_REQUIRED,
                    USER_INVALID_TOKEN_CLAIM,
                    USER_MYPAGE_PASSWORD_CHANGE_REQUIRED,
                    USER_SUSPENDED -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
