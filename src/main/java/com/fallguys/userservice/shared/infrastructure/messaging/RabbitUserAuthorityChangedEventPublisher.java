package com.fallguys.userservice.shared.infrastructure.messaging;

import com.fallguys.userservice.shared.infrastructure.persistence.outbox.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RabbitUserAuthorityChangedEventPublisher {

    private static final long PUBLISH_CONFIRM_TIMEOUT_MILLIS = 5_000L;

    private final RabbitTemplate rabbitTemplate;

    public void publish(OutboxEventEntity event) {
        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();

        CorrelationData correlationData = new CorrelationData(event.getEventId());
        rabbitTemplate.send(event.getExchangeName(), event.getRoutingKey(), message, correlationData);
        waitForPublishConfirm(correlationData);
    }

    private void waitForPublishConfirm(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(PUBLISH_CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ publish not acknowledged: " + confirm.reason());
            }

            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new IllegalStateException("RabbitMQ returned message: " + returned.getReplyText());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ publish confirm", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to confirm RabbitMQ publish", ex);
        }
    }
}
