package com.fallguys.userservice.shared.infrastructure.outbox;

import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OutboxEventJpaDaoTest {

    @Autowired
    private OutboxEventJpaDao outboxEventJpaDao;

    @Test
    void savedAuthorityChangedOutboxEventIsPendingUntilRelayPublishesIt() {
        OutboxEventEntity event = OutboxEventEntity.pending(
                "event-001",
                "user.authority.changed",
                "USER",
                "4997ac1b-eb49-48fc-858c-a009f30b0533",
                "erp.events",
                "user.authority.changed.gateway",
                """
                        {
                          "eventId": "event-001",
                          "eventType": "user.authority.changed",
                          "eventVersion": 1,
                          "producer": "user-service",
                          "occurredAt": "2026-06-23T13:00:05Z",
                          "correlationId": "USER-4997ac1b-eb49-48fc-858c-a009f30b0533",
                          "payload": {
                            "keycloakSub": "4997ac1b-eb49-48fc-858c-a009f30b0533",
                            "employeeNo": "ADMIN002",
                            "reason": "USER_PROFILE_UPDATED"
                          }
                        }
                        """,
                Instant.parse("2026-06-23T13:00:05Z")
        );

        outboxEventJpaDao.saveAndFlush(event);

        List<OutboxEventEntity> pendingEvents = outboxEventJpaDao.findByStatusOrderByCreatedAt(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 10)
        );
        assertThat(pendingEvents)
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getEventId()).isEqualTo("event-001");
                    assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
                    assertThat(saved.getExchangeName()).isEqualTo("erp.events");
                    assertThat(saved.getRoutingKey()).isEqualTo("user.authority.changed.gateway");
                    assertThat(saved.getPublishedAt()).isNull();
                });
    }
}
