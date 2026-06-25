package com.fallguys.userservice.shared.domain.activity;

import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    /**
     * 사용자 활동 발생 이벤트를 최근 활동 로그로 저장한다.
     *
     * 흐름:
     * 1) producer가 완성해서 보낸 action, title, content, status로 저장 대상을 만든다.
     * 2) eventId unique 제약 기반 원자적 저장을 시도한다.
     * 3) 이미 저장된 eventId면 재수신으로 보고 성공 처리한다.
     *
     * 트랜잭션: 쓰기. 중복 eventId는 멱등 성공으로 처리하고, 그 외 저장 실패는 RabbitMQ retry/DLQ 정책에 맡긴다.
     *
     * 예외:
     * - 필수 이벤트 값 누락: UserException(USER_INVALID_REQUEST), 저장 중단.
     * - DB 저장 실패: 런타임 예외, 트랜잭션 롤백.
     */
    @Transactional
    public void record(CreateActivityLogCommand command) {
        activityLogRepository.saveIfAbsent(ActivityLog.create(command));
    }

    /**
     * 사번 기준 최근 활동 로그를 조회한다.
     *
     * 흐름:
     * 1) 조회 대상 사번과 조회 개수를 검증한다.
     * 2) occurredAt, id 역순으로 최근 활동 로그를 조회한다.
     *
     * 트랜잭션: 읽기 전용. 활동 로그를 변경하지 않는다.
     *
     * 예외:
     * - 사번 누락 또는 잘못된 조회 개수: UserException(USER_INVALID_REQUEST), 조회 중단.
     */
    @Transactional(readOnly = true)
    public List<ActivityLog> findRecentByEmployeeNo(String employeeNo, int limit) {
        if (employeeNo == null || employeeNo.isBlank() || limit < 1) {
            throw new UserException(UserErrorCode.USER_INVALID_REQUEST);
        }

        return activityLogRepository.findRecentByEmployeeNo(employeeNo, limit);
    }
}
