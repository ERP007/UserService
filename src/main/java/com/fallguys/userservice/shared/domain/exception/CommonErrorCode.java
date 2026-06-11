package com.fallguys.userservice.shared.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    AUTHENTICATION_REQUIRED("ER-401", "인증 필요"),
    ACCESS_DENIED("ER-403", "권한 없음"),
    SERVER_ERROR("ER-500", "서버 내부 에러"),
    BAD_GATEWAY("ER-502", "internal API 실패");

    private final String code;
    private final String message;
}
