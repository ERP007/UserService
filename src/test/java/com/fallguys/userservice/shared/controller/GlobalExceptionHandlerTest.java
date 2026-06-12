package com.fallguys.userservice.shared.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fallguys.userservice.shared.domain.exception.CommonErrorCode;
import com.fallguys.userservice.shared.domain.exception.CommonException;
import com.fallguys.userservice.shared.domain.exception.ErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.exception.UserIdentityException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAuthenticationCommonErrorToCommonErrorCode() {
        ProblemDetail problemDetail = handler.handleBusiness(
                new CommonException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        );

        assertProblem(problemDetail, 401, CommonErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void mapsForbiddenUserErrorToCommonErrorCode() {
        ProblemDetail problemDetail = handler.handleBusiness(
                new UserException(UserErrorCode.USER_ADMIN_REQUIRED)
        );

        assertProblem(problemDetail, 403, CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void mapsAuthenticationExceptionToCommonErrorCode() {
        ProblemDetail problemDetail = handler.handleAuthentication(new BadCredentialsException("bad token"));

        assertProblem(problemDetail, 401, CommonErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void mapsAccessDeniedExceptionToCommonErrorCode() {
        ProblemDetail problemDetail = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertProblem(problemDetail, 403, CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void mapsUserIdentityExceptionToCommonBadGateway() {
        ProblemDetail problemDetail = handler.handleUserIdentity(
                new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED)
        );

        assertProblem(problemDetail, 502, CommonErrorCode.BAD_GATEWAY);
    }

    @Test
    void mapsUnexpectedExceptionToCommonServerError() {
        ProblemDetail problemDetail = handler.handleException(new RuntimeException("boom"));

        assertProblem(problemDetail, 500, CommonErrorCode.SERVER_ERROR);
    }

    @Test
    void keepsUserErrorCodeForDomainNotFound() {
        ProblemDetail problemDetail = handler.handleBusiness(
                new UserException(UserErrorCode.USER_NOT_FOUND)
        );

        assertProblem(problemDetail, 404, UserErrorCode.USER_NOT_FOUND);
    }

    private void assertProblem(ProblemDetail problemDetail, int status, ErrorCode errorCode) {
        assertThat(problemDetail.getStatus()).isEqualTo(status);
        assertThat(problemDetail.getDetail()).isEqualTo(errorCode.getMessage());
        assertThat(problemDetail.getProperties()).containsEntry("errorCode", errorCode.getCode());
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
    }
}
