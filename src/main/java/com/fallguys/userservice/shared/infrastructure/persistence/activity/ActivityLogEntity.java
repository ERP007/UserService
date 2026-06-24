package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import com.fallguys.userservice.shared.domain.activity.ActivityLog;
import com.fallguys.userservice.shared.domain.activity.UserActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "activity_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_activity_logs_event_id", columnNames = "event_id")
        },
        indexes = {
                @Index(name = "idx_activity_logs_employee_occurred", columnList = "employee_no, occurred_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "employee_no", nullable = false, length = 50)
    private String employeeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private UserActionType action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", length = 100)
    private String content;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "producer", nullable = false, length = 50)
    private String producer;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private ActivityLogEntity(
            String eventId,
            String employeeNo,
            UserActionType action,
            Instant occurredAt,
            String title,
            String content,
            String status,
            String producer,
            String correlationId
    ) {
        this.eventId = eventId;
        this.employeeNo = employeeNo;
        this.action = action;
        this.occurredAt = occurredAt;
        this.title = title;
        this.content = content;
        this.status = status;
        this.producer = producer;
        this.correlationId = correlationId;
    }

    public static ActivityLogEntity from(ActivityLog activityLog) {
        return new ActivityLogEntity(
                activityLog.getEventId(),
                activityLog.getEmployeeNo(),
                activityLog.getAction(),
                activityLog.getOccurredAt(),
                activityLog.getTitle(),
                activityLog.getContent(),
                activityLog.getStatus(),
                activityLog.getProducer(),
                activityLog.getCorrelationId()
        );
    }

    public ActivityLog toDomain() {
        return ActivityLog.restore(
                id,
                eventId,
                employeeNo,
                action,
                occurredAt,
                title,
                content,
                status,
                producer,
                correlationId,
                createdAt
        );
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
