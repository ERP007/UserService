package com.fallguys.userservice.shared.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_ALREADY_EXISTS("USR-001", "이미 등록된 사용자입니다."),
    USER_NOT_FOUND("USR-002", "사용자를 찾을 수 없습니다."),
    USER_ADMIN_REQUIRED("USR-003", "관리자만 이용할 수 있는 API입니다."),
    USER_INVALID_TOKEN_CLAIM("USR-004", "토큰의 사용자 권한 정보를 확인할 수 없습니다."),
    USER_INVALID_REQUEST("USR-005", "입력값을 확인해주세요."),
    USER_REQUIRED_PARAMETER("USR-006", "필수 요청 파라미터가 누락되었습니다."),
    USER_UNSUPPORTED_PARAMETER("USR-007", "지원하지 않는 요청 파라미터 값입니다."),
    USER_INVALID_PAGE("USR-008", "페이지 번호는 1 이상이어야 합니다."),
    USER_INVALID_SIZE("USR-009", "페이지 크기는 1 이상 100 이하만 가능합니다."),
    USER_EMPLOYEE_NUMBER_REQUIRED("USR-010", "사번을 입력해주세요."),
    USER_EMPLOYEE_NUMBERS_REQUIRED("USR-011", "사번 목록을 입력해주세요."),
    USER_BATCH_SIZE_EXCEEDED("USR-012", "사번은 한 번에 최대 100개까지 조회할 수 있습니다."),
    USER_TENANCY_NOT_FOUND("USR-013", "소속을 찾을 수 없습니다."),
    USER_UNSUPPORTED_TENANCY("USR-014", "지원하지 않는 소속 타입입니다."),
    USER_TENANCY_MISMATCH("USR-015", "소속 코드와 소속 타입이 일치하지 않습니다."),
    USER_ROLE_UNSUPPORTED("USR-016", "지원하지 않는 Role입니다."),
    USER_PASSWORD_ISSUE_MODE_REQUIRED("USR-017", "초기 비밀번호 발급 방식을 선택해주세요."),
    USER_PASSWORD_ISSUE_MODE_UNSUPPORTED("USR-018", "지원하지 않는 초기 비밀번호 발급 방식입니다."),
    USER_INITIAL_PASSWORD_AUTO_NOT_ALLOWED("USR-019", "자동 발급 방식에서는 초기 비밀번호를 직접 전달할 수 없습니다."),
    USER_MYPAGE_PASSWORD_CHANGE_REQUIRED("USR-020", "비밀번호 변경 전까지 마이페이지에 접근할 수 없습니다."),
    USER_SUSPENDED("USR-021", "정지된 사용자는 서비스를 이용할 수 없습니다."),
    USER_TEMPORARY_PASSWORD_INVALID("USR-022", "임시 비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다."),
    USER_IDENTITY_READ_FAILED("USR-101", "사용자 인증 정보를 조회하지 못했습니다."),
    USER_IDENTITY_CREATE_FAILED("USR-102", "사용자 인증 정보를 생성하지 못했습니다."),
    USER_IDENTITY_PASSWORD_RESET_FAILED("USR-103", "사용자 임시 비밀번호를 설정하지 못했습니다."),
    USER_IDENTITY_DELETE_FAILED("USR-104", "사용자 인증 정보를 삭제하지 못했습니다."),
    USER_IDENTITY_ENABLED_UPDATE_FAILED("USR-105", "사용자 정지 상태를 변경하지 못했습니다."),
    USER_IDENTITY_UPDATE_FAILED("USR-106", "사용자 인증 정보를 수정하지 못했습니다.");

    private final String code;
    private final String message;
}
