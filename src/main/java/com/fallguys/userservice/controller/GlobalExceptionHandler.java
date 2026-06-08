package com.fallguys.userservice.controller;

import java.time.Instant;

import com.fallguys.userservice.domain.exception.BusinessException;
import com.fallguys.userservice.domain.exception.UserIdentityException;
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

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("User business exception. code={}", ex.getCode());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    private ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        return problemDetail;
    }
}
