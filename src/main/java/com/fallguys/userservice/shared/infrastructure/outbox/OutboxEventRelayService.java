package com.fallguys.userservice.shared.infrastructure.outbox;

import com.fallguys.userservice.shared.infrastructure.messaging.RabbitUserAuthorityChangedEventPublisher;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class OutboxEventRelayService {

    private final OutboxEventJpaDao outboxEventJpaDao;
    private final RabbitUserAuthorityChangedEventPublisher rabbitPublisher;
    private final int maxAttempts;

    public OutboxEventRelayService(
            OutboxEventJpaDao outboxEventJpaDao,
            RabbitUserAuthorityChangedEventPublisher rabbitPublisher,
            @Value("${outbox.relay.max-attempts:10}") int maxAttempts
    ) {
        this.outboxEventJpaDao = outboxEventJpaDao;
        this.rabbitPublisher = rabbitPublisher;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingEventIds(int batchSize) {
        return outboxEventJpaDao.findByStatusOrderByCreatedAt(
                        OutboxEventStatus.PENDING,
                        PageRequest.of(0, batchSize)
                ).stream()
                .map(OutboxEventEntity::getId)
                .toList();
    }

    @Transactional
    public void relay(Long eventId) {
        OutboxEventEntity event = outboxEventJpaDao.findById(eventId)
                .filter(found -> found.getStatus() == OutboxEventStatus.PENDING)
                .orElse(null);
        if (event == null) {
            return;
        }

        try {
            rabbitPublisher.publish(event);
            event.markPublished(Instant.now());
        } catch (RuntimeException ex) {
            event.markPublishFailed(ex, maxAttempts);
            log.warn(
                    "Outbox 이벤트 발행 실패. eventId={}, routingKey={}, retryCount={}, status={}",
                    event.getEventId(),
                    event.getRoutingKey(),
                    event.getRetryCount(),
                    event.getStatus(),
                    ex
            );
        }
    }
}
