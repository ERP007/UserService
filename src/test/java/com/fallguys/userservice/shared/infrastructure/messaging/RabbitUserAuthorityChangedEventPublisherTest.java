package com.fallguys.userservice.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventJpaDao;
import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventStatus;
import com.fallguys.userservice.usermanagement.domain.UserAuthorityChangedEvent;
import com.fallguys.userservice.usermanagement.domain.UserSessionLogoutEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RabbitUserAuthorityChangedEventPublisherTest {

    @Test
    void storesUserAuthorityChangedMessageAsPendingOutboxEvent() throws Exception {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        UserAuthorityRabbitProperties properties = new UserAuthorityRabbitProperties();
        OutboxUserAuthorityChangedEventPublisher publisher =
                new OutboxUserAuthorityChangedEventPublisher(outboxEventJpaDao, properties, new ObjectMapper());

        publisher.publish(new UserAuthorityChangedEvent(
                "4997ac1b-eb49-48fc-858c-a009f30b0533",
                "ADMIN002"
        ));

        ArgumentCaptor<OutboxEventEntity> eventCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventJpaDao).save(eventCaptor.capture());
        OutboxEventEntity event = eventCaptor.getValue();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getExchangeName()).isEqualTo("erp.events");
        assertThat(event.getRoutingKey()).isEqualTo("user.authority.changed.gateway");
        assertThat(event.getAggregateType()).isEqualTo("USER");
        assertThat(event.getAggregateId()).isEqualTo("4997ac1b-eb49-48fc-858c-a009f30b0533");

        UserAuthorityChangedMessage message = new ObjectMapper()
                .readValue(event.getPayload(), UserAuthorityChangedMessage.class);
        assertThat(message.eventId()).isNotBlank();
        assertThat(message.eventId()).isEqualTo(event.getEventId());
        assertThat(message.eventType()).isEqualTo("user.authority.changed");
        assertThat(message.eventVersion()).isEqualTo(1);
        assertThat(message.producer()).isEqualTo("user-service");
        assertThat(message.occurredAt()).isNotBlank();
        assertThat(message.correlationId()).isEqualTo("USER-4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(message.payload().keycloakSub()).isEqualTo("4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(message.payload().employeeNo()).isEqualTo("ADMIN002");
        assertThat(message.payload().reason()).isEqualTo("USER_PROFILE_UPDATED");
    }

    @Test
    void storesUserSessionLogoutMessageAsPendingOutboxEvent() throws Exception {
        OutboxEventJpaDao outboxEventJpaDao = mock(OutboxEventJpaDao.class);
        OutboxUserSessionLogoutEventPublisher publisher =
                new OutboxUserSessionLogoutEventPublisher(outboxEventJpaDao, new ObjectMapper());

        publisher.publish(new UserSessionLogoutEvent("4997ac1b-eb49-48fc-858c-a009f30b0533"));

        ArgumentCaptor<OutboxEventEntity> eventCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventJpaDao).save(eventCaptor.capture());
        OutboxEventEntity event = eventCaptor.getValue();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getEventType()).isEqualTo("keycloak.user.sessions.logout");
        assertThat(event.getExchangeName()).isEqualTo("internal");
        assertThat(event.getRoutingKey()).isEqualTo("keycloak.user.sessions.logout");
        assertThat(event.getAggregateType()).isEqualTo("USER");
        assertThat(event.getAggregateId()).isEqualTo("4997ac1b-eb49-48fc-858c-a009f30b0533");

        UserSessionLogoutMessage message = new ObjectMapper()
                .readValue(event.getPayload(), UserSessionLogoutMessage.class);
        assertThat(message.eventId()).isNotBlank();
        assertThat(message.eventId()).isEqualTo(event.getEventId());
        assertThat(message.eventType()).isEqualTo("keycloak.user.sessions.logout");
        assertThat(message.eventVersion()).isEqualTo(1);
        assertThat(message.producer()).isEqualTo("user-service");
        assertThat(message.occurredAt()).isNotBlank();
        assertThat(message.correlationId()).isEqualTo("USER-4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(message.payload().keycloakSub()).isEqualTo("4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(message.payload().reason()).isEqualTo("SESSION_SCOPED_PROFILE_UPDATED");
    }

    @Test
    void publishesPendingOutboxEventToRabbitWithJsonBody() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitUserAuthorityChangedEventPublisher publisher =
                new RabbitUserAuthorityChangedEventPublisher(rabbitTemplate);
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
                java.time.Instant.parse("2026-06-23T13:00:05Z")
        );
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq("erp.events"),
                eq("user.authority.changed.gateway"),
                any(Message.class),
                any(CorrelationData.class)
        );

        publisher.publish(event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(
                eq("erp.events"),
                eq("user.authority.changed.gateway"),
                messageCaptor.capture(),
                correlationDataCaptor.capture()
        );
        Message message = messageCaptor.getValue();
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo("event-001");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"eventType\": \"user.authority.changed\"")
                .contains("\"keycloakSub\": \"4997ac1b-eb49-48fc-858c-a009f30b0533\"");
    }

    @Test
    void rabbitMessageConverterSerializesGatewayContractMessage() {
        MessageConverter messageConverter = new UserAuthorityRabbitConfig().rabbitMessageConverter();
        UserAuthorityChangedMessage source = UserAuthorityChangedMessage.from(new UserAuthorityChangedEvent(
                "4997ac1b-eb49-48fc-858c-a009f30b0533",
                "ADMIN002"
        ));

        Message message = messageConverter.toMessage(source, new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(UserAuthorityChangedMessage.class);

        Object converted = messageConverter.fromMessage(message);

        assertThat(message.getMessageProperties().getContentType()).contains("json");
        assertThat(converted).isInstanceOf(UserAuthorityChangedMessage.class);
        UserAuthorityChangedMessage roundTripped = (UserAuthorityChangedMessage) converted;
        assertThat(roundTripped.eventType()).isEqualTo("user.authority.changed");
        assertThat(roundTripped.producer()).isEqualTo("user-service");
        assertThat(roundTripped.correlationId()).isEqualTo("USER-4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(roundTripped.payload().keycloakSub()).isEqualTo("4997ac1b-eb49-48fc-858c-a009f30b0533");
        assertThat(roundTripped.payload().employeeNo()).isEqualTo("ADMIN002");
        assertThat(roundTripped.payload().reason()).isEqualTo("USER_PROFILE_UPDATED");
    }
}
