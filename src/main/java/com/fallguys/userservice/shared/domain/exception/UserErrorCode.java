package com.fallguys.userservice.shared.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode {
    USER_ALREADY_EXISTS("USR-002", "이미 등록된 사용자입니다."),
    USER_IDENTITY_READ_FAILED("USR-101", "사용자 인증 정보를 조회하지 못했습니다."),
    USER_IDENTITY_CREATE_FAILED("USR-102", "사용자 인증 정보를 생성하지 못했습니다."),
    USER_IDENTITY_PASSWORD_RESET_FAILED("USR-103", "사용자 임시 비밀번호를 설정하지 못했습니다."),
    USER_IDENTITY_DELETE_FAILED("USR-104", "사용자 인증 정보를 삭제하지 못했습니다."),
    USER_IDENTITY_ENABLED_UPDATE_FAILED("USR-105", "사용자 정지 상태를 변경하지 못했습니다."),
    USER_IDENTITY_UPDATE_FAILED("USR-106", "사용자 인증 정보를 수정하지 못했습니다.");

    private final String code;
    private final String message;
}
