package com.fallguys.userservice.shared.infrastructure.persistence.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventJpaDao extends JpaRepository<OutboxEventEntity, Long> {

    @Query("""
            select event
            from OutboxEventEntity event
            where event.status = :status
            order by event.createdAt asc
            """)
    List<OutboxEventEntity> findByStatusOrderByCreatedAt(
            @Param("status") OutboxEventStatus status,
            Pageable pageable
    );
}
