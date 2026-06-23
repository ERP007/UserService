package com.fallguys.userservice.shared.infrastructure.persistence.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_outbox_events_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type, aggregate_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 150)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private OutboxEventEntity(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String exchangeName,
            String routingKey,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = OutboxEventStatus.PENDING;
    }

    public static OutboxEventEntity pending(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String exchangeName,
            String routingKey,
            String payload,
            Instant occurredAt
    ) {
        return new OutboxEventEntity(
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                exchangeName,
                routingKey,
                payload,
                occurredAt
        );
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markPublishFailed(RuntimeException failure, int maxAttempts) {
        this.retryCount++;
        this.lastError = truncate(failure.getMessage());
        if (retryCount >= maxAttempts) {
            this.status = OutboxEventStatus.FAILED;
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= LAST_ERROR_MAX_LENGTH) {
            return message;
        }

        return message.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
