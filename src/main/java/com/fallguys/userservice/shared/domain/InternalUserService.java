package com.fallguys.userservice.shared.domain;

import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import com.fallguys.userservice.shared.domain.query.BatchUser;
import com.fallguys.userservice.shared.domain.query.BatchUserListResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalUserService {

    private static final int MAX_BATCH_USER_LOOKUP_SIZE = 100;

    private final InternalUserRepository userRepository;

    /**
     * 사번 목록으로 사용자 이름과 직급을 배치 조회한다.
     *
     * 흐름:
     * 1) 요청 사번 목록에서 공백 값을 제거하고 중복을 정리한 뒤 최대 조회 개수를 검증한다.
     * 2) InternalUserRepository에 정리된 사번 목록을 전달해 IN 조건으로 한 번에 조회한다.
     * 3) 요청 순서를 기준으로 조회된 사용자와 찾지 못한 사번을 분리해 반환한다.
     *
     * 트랜잭션: 읽기 전용. 발주 서비스 등 내부 호출자가 담당자 표시 정보를 조회할 때 사용하며 상태를 변경하지 않는다.
     *
     * 예외:
     * - 사번 목록 누락, 전부 공백, 최대 개수 초과: UserException(400 매핑), 조회 중단.
     */
    @Transactional(readOnly = true)
    public BatchUserListResult findBatchUsers(List<String> employeeNumbers) {
        List<String> requestedEmployeeNumbers = normalizeEmployeeNumbers(employeeNumbers);
        List<String> lookupEmployeeNumbers = requestedEmployeeNumbers.stream()
                .map(this::employeeNumberKey)
                .toList();
        List<BatchUser> foundUsers = userRepository.findBatchUsersByEmployeeNumbers(lookupEmployeeNumbers);
        Map<String, BatchUser> foundByEmployeeNumber = new LinkedHashMap<>();
        foundUsers.forEach(user -> foundByEmployeeNumber.putIfAbsent(employeeNumberKey(user.employeeNumber()), user));

        List<BatchUser> orderedUsers = requestedEmployeeNumbers.stream()
                .map(employeeNumber -> foundByEmployeeNumber.get(employeeNumberKey(employeeNumber)))
                .filter(Objects::nonNull)
                .toList();
        List<String> notFoundEmployeeNumbers = requestedEmployeeNumbers.stream()
                .filter(employeeNumber -> !foundByEmployeeNumber.containsKey(employeeNumberKey(employeeNumber)))
                .toList();

        return new BatchUserListResult(orderedUsers, notFoundEmployeeNumbers);
    }

    /**
     * 사번으로 내부 서비스용 사용자 표시 정보를 단건 조회한다.
     *
     * 흐름:
     * 1) 사번 1건을 기존 배치 조회 규칙과 동일하게 정규화한다.
     * 2) 정규화된 사번으로 사용자 이름과 직급을 조회한다.
     * 3) 조회 결과가 없으면 404로 중단한다.
     *
     * 트랜잭션: 읽기 전용. 내부 서비스가 담당자 표시 정보를 조회할 때 사용하며 상태를 변경하지 않는다.
     *
     * 예외:
     * - 사번 누락 또는 공백: UserException(400 매핑), 조회 중단.
     * - 사용자 없음: UserException(404 매핑), 조회 중단.
     */
    @Transactional(readOnly = true)
    public BatchUser findByEmployeeNum(String employeeNumber) {
        if (!hasText(employeeNumber)) {
            throw new UserException(UserErrorCode.USER_EMPLOYEE_NUMBER_REQUIRED);
        }

        return findBatchUsers(List.of(employeeNumber)).users().stream()
                .findFirst()
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private List<String> normalizeEmployeeNumbers(List<String> employeeNumbers) {
        if (employeeNumbers == null) {
            throw new UserException(UserErrorCode.USER_EMPLOYEE_NUMBERS_REQUIRED);
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        employeeNumbers.stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(employeeNumber -> normalized.putIfAbsent(employeeNumberKey(employeeNumber), employeeNumber));

        if (normalized.isEmpty()) {
            throw new UserException(UserErrorCode.USER_EMPLOYEE_NUMBERS_REQUIRED);
        }
        if (normalized.size() > MAX_BATCH_USER_LOOKUP_SIZE) {
            throw new UserException(UserErrorCode.USER_BATCH_SIZE_EXCEEDED);
        }

        return List.copyOf(normalized.values());
    }

    private String employeeNumberKey(String employeeNumber) {
        return employeeNumber.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
