package com.fallguys.userservice.shared.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fallguys.userservice.shared.domain.activity.UserActionType;
import com.fallguys.userservice.shared.domain.command.CreateActivityLogCommand;
import com.fallguys.userservice.shared.domain.exception.UserException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

class UserActivityOccurredMessageTest {

    @Test
    void convertsUserActivityOccurredMessageToCommand() {
        UserActivityOccurredMessage message = message();

        CreateActivityLogCommand command = message.toCommand();

        assertThat(command.eventId()).isEqualTo("7c3e0b76-44d0-4f53-9e65-200000000013");
        assertThat(command.employeeNo()).isEqualTo("ADMIN002");
        assertThat(command.action()).isEqualTo(UserActionType.STOCK_ADJUSTED);
        assertThat(command.occurredAt()).isEqualTo(Instant.parse("2026-06-24T10:15:30Z"));
        assertThat(command.title()).isEqualTo("엔진오일 필터");
        assertThat(command.content()).isEqualTo("HMC-EN-00214");
        assertThat(command.status()).isEqualTo("-3");
        assertThat(command.producer()).isEqualTo("inventory-service");
        assertThat(command.correlationId()).isEqualTo("INV-7c3e0b76-44d0-4f53-9e65-200000000013");
    }

    @Test
    void rejectsUnsupportedEventVersion() {
        UserActivityOccurredMessage message = new UserActivityOccurredMessage(
                "7c3e0b76-44d0-4f53-9e65-200000000013",
                UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_ROUTING_KEY,
                2,
                "inventory-service",
                Instant.parse("2026-06-24T10:15:30Z"),
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013",
                new UserActivityOccurredMessage.UserActivityOccurredPayload(
                        "ADMIN002",
                        UserActionType.STOCK_ADJUSTED,
                        Instant.parse("2026-06-24T10:15:30Z"),
                        "엔진오일 필터",
                        "HMC-EN-00214",
                        "-3"
                )
        );

        assertThatThrownBy(message::toCommand)
                .isInstanceOf(UserException.class);
    }

    @Test
    void rabbitMessageConverterDeserializesUserActivityOccurredMessage() {
        MessageConverter messageConverter = new UserAuthorityRabbitConfig().rabbitMessageConverter();

        Message message = messageConverter.toMessage(message(), new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(UserActivityOccurredMessage.class);

        Object converted = messageConverter.fromMessage(message);

        assertThat(converted).isInstanceOf(UserActivityOccurredMessage.class);
        UserActivityOccurredMessage roundTripped = (UserActivityOccurredMessage) converted;
        assertThat(roundTripped.eventId()).isEqualTo("7c3e0b76-44d0-4f53-9e65-200000000013");
        assertThat(roundTripped.payload().action()).isEqualTo(UserActionType.STOCK_ADJUSTED);
        assertThat(roundTripped.payload().occurredAt()).isEqualTo(Instant.parse("2026-06-24T10:15:30Z"));
    }

    private UserActivityOccurredMessage message() {
        return new UserActivityOccurredMessage(
                "7c3e0b76-44d0-4f53-9e65-200000000013",
                UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_ROUTING_KEY,
                UserActivityRabbitConfig.USER_ACTIVITY_OCCURRED_EVENT_VERSION,
                "inventory-service",
                Instant.parse("2026-06-24T10:15:30Z"),
                "INV-7c3e0b76-44d0-4f53-9e65-200000000013",
                new UserActivityOccurredMessage.UserActivityOccurredPayload(
                        "ADMIN002",
                        UserActionType.STOCK_ADJUSTED,
                        Instant.parse("2026-06-24T10:15:30Z"),
                        "엔진오일 필터",
                        "HMC-EN-00214",
                        "-3"
                )
        );
    }
}
