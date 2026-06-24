package com.fallguys.userservice.shared.domain.activity;

import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
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
     * 1) eventId가 이미 저장된 이벤트인지 확인한다.
     * 2) 중복 이벤트면 재수신으로 보고 성공 처리한다.
     * 3) producer가 완성해서 보낸 action, title, content, status를 저장한다.
     *
     * 트랜잭션: 쓰기. 저장 실패 시 메시지 처리는 실패하고 RabbitMQ retry/DLQ 정책에 맡긴다.
     *
     * 예외:
     * - 필수 이벤트 값 누락: UserException(USER_INVALID_REQUEST), 저장 중단.
     * - DB 저장 실패: 런타임 예외, 트랜잭션 롤백.
     */
    @Transactional
    public void record(CreateActivityLogCommand command) {
        if (activityLogRepository.existsByEventId(command.eventId())) {
            return;
        }

        activityLogRepository.save(ActivityLog.create(command));
    }
}
