package com.fallguys.userservice.shared.controller;

import java.time.Instant;

import com.fallguys.userservice.shared.domain.exception.BusinessException;
import com.fallguys.userservice.shared.domain.exception.CommonErrorCode;
import com.fallguys.userservice.shared.domain.exception.ErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserAlreadyExistsException;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserIdentityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserIdentityException.class)
    public ProblemDetail handleUserIdentity(UserIdentityException ex) {
        log.error("User identity exception. code={}", ex.getCode(), ex);
        return build(statusOf(CommonErrorCode.BAD_GATEWAY), CommonErrorCode.BAD_GATEWAY);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists. code={}", ex.getCode());
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication exception. message={}", ex.getMessage());
        return build(statusOf(CommonErrorCode.AUTHENTICATION_REQUIRED), CommonErrorCode.AUTHENTICATION_REQUIRED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied exception. message={}", ex.getMessage());
        return build(statusOf(CommonErrorCode.ACCESS_DENIED), CommonErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("User business exception. code={}", ex.getCode());
        ErrorCode errorCode = commonErrorCodeOf(ex.getErrorCode());
        return build(statusOf(errorCode), errorCode);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid user request. message={}", ex.getMessage());
        UserErrorCode errorCode = UserErrorCode.USER_INVALID_REQUEST;
        return build(HttpStatus.BAD_REQUEST, errorCode);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex) {
        log.error("Unexpected user service exception.", ex);
        return build(statusOf(CommonErrorCode.SERVER_ERROR), CommonErrorCode.SERVER_ERROR);
    }

    private ProblemDetail build(HttpStatus status, ErrorCode errorCode) {
        return build(status, errorCode.getCode(), errorCode.getMessage());
    }

    private ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        return problemDetail;
    }

    private ErrorCode commonErrorCodeOf(ErrorCode errorCode) {
        if (errorCode instanceof UserErrorCode userErrorCode) {
            return switch (userErrorCode) {
                case USER_ADMIN_REQUIRED,
                        USER_INVALID_TOKEN_CLAIM,
                        USER_MYPAGE_PASSWORD_CHANGE_REQUIRED,
                        USER_SUSPENDED -> CommonErrorCode.ACCESS_DENIED;
                default -> userErrorCode;
            };
        }

        return errorCode;
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        if (errorCode instanceof CommonErrorCode commonErrorCode) {
            return switch (commonErrorCode) {
                case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
                case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
                case SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
                case BAD_GATEWAY -> HttpStatus.BAD_GATEWAY;
            };
        }
        if (errorCode instanceof UserErrorCode userErrorCode) {
            return switch (userErrorCode) {
                case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case USER_ALREADY_EXISTS -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_REQUEST;
            };
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
