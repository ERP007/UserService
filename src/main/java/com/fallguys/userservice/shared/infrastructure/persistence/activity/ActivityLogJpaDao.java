package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogJpaDao extends JpaRepository<ActivityLogEntity, Long> {

    boolean existsByEventId(String eventId);
}
