package com.fallguys.userservice.shared.infrastructure.persistence.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
