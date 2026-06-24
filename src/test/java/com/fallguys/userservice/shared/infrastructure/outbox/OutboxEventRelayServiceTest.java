package com.fallguys.userservice.shared.infrastructure.outbox;

import com.fallguys.userservice.shared.infrastructure.messaging.RabbitUserAuthorityChangedEventPublisher;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventRelayServiceTest {

    @Test
    void relayMarksEventPublishedWhenRabbitPublishSucceeds() {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        RabbitUserAuthorityChangedEventPublisher rabbitPublisher =
                mock(RabbitUserAuthorityChangedEventPublisher.class);
        UserIdentityManager userIdentityManager = mock(UserIdentityManager.class);
        OutboxEventRelayService relayService =
                new OutboxEventRelayService(outboxEventJpaDao, rabbitPublisher, userIdentityManager, 3);
        OutboxEventEntity event = pendingEvent();
        when(outboxEventJpaDao.findById(1L)).thenReturn(Optional.of(event));

        relayService.relay(1L);

        verify(rabbitPublisher).publish(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void relayLogsOutKeycloakSessionsWhenLogoutEventSucceeds() {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        RabbitUserAuthorityChangedEventPublisher rabbitPublisher =
                mock(RabbitUserAuthorityChangedEventPublisher.class);
        UserIdentityManager userIdentityManager = mock(UserIdentityManager.class);
        OutboxEventRelayService relayService =
                new OutboxEventRelayService(outboxEventJpaDao, rabbitPublisher, userIdentityManager, 3);
        OutboxEventEntity event = pendingLogoutEvent();
        when(outboxEventJpaDao.findById(1L)).thenReturn(Optional.of(event));

        relayService.relay(1L);

        verify(userIdentityManager).logoutSessions("4997ac1b-eb49-48fc-858c-a009f30b0533");
        verify(rabbitPublisher, never()).publish(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void relayKeepsEventPendingWhenRabbitPublishFailsBeforeMaxAttempts() {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        RabbitUserAuthorityChangedEventPublisher rabbitPublisher =
                mock(RabbitUserAuthorityChangedEventPublisher.class);
        UserIdentityManager userIdentityManager = mock(UserIdentityManager.class);
        OutboxEventRelayService relayService =
                new OutboxEventRelayService(outboxEventJpaDao, rabbitPublisher, userIdentityManager, 3);
        OutboxEventEntity event = pendingEvent();
        RuntimeException failure = new RuntimeException("rabbitmq down");
        when(outboxEventJpaDao.findById(1L)).thenReturn(Optional.of(event));
        doThrow(failure).when(rabbitPublisher).publish(event);

        relayService.relay(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("rabbitmq down");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void relayMarksEventFailedWhenRabbitPublishFailuresReachMaxAttempts() {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        RabbitUserAuthorityChangedEventPublisher rabbitPublisher =
                mock(RabbitUserAuthorityChangedEventPublisher.class);
        UserIdentityManager userIdentityManager = mock(UserIdentityManager.class);
        OutboxEventRelayService relayService =
                new OutboxEventRelayService(outboxEventJpaDao, rabbitPublisher, userIdentityManager, 1);
        OutboxEventEntity event = pendingEvent();
        RuntimeException failure = new RuntimeException("rabbitmq down");
        when(outboxEventJpaDao.findById(1L)).thenReturn(Optional.of(event));
        doThrow(failure).when(rabbitPublisher).publish(event);

        relayService.relay(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("rabbitmq down");
        assertThat(event.getPublishedAt()).isNull();
    }

    private OutboxEventEntity pendingEvent() {
        return OutboxEventEntity.pending(
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
    }

    private OutboxEventEntity pendingLogoutEvent() {
        return OutboxEventEntity.pending(
                "event-002",
                "keycloak.user.sessions.logout",
                "USER",
                "4997ac1b-eb49-48fc-858c-a009f30b0533",
                "internal",
                "keycloak.user.sessions.logout",
                """
                        {
                          "eventId": "event-002",
                          "eventType": "keycloak.user.sessions.logout",
                          "eventVersion": 1,
                          "producer": "user-service",
                          "occurredAt": "2026-06-23T13:00:05Z",
                          "correlationId": "USER-4997ac1b-eb49-48fc-858c-a009f30b0533",
                          "payload": {
                            "keycloakSub": "4997ac1b-eb49-48fc-858c-a009f30b0533",
                            "reason": "SESSION_SCOPED_PROFILE_UPDATED"
                          }
                        }
                        """,
                Instant.parse("2026-06-23T13:00:05Z")
        );
    }
}
