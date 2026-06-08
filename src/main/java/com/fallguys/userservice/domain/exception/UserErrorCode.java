package com.fallguys.userservice.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode {
    USER_ALREADY_EXISTS("USR-002", "이미 등록된 사용자입니다."),
    USER_IDENTITY_READ_FAILED("USR-101", "사용자 인증 정보를 조회하지 못했습니다."),
    USER_IDENTITY_CREATE_FAILED("USR-102", "사용자 인증 정보를 생성하지 못했습니다."),
    USER_IDENTITY_DELETE_FAILED("USR-104", "사용자 인증 정보를 삭제하지 못했습니다.");

    private final String code;
    private final String message;
}
