package com.fallguys.userservice.shared.infrastructure.persistence.activity;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogJpaDao extends JpaRepository<ActivityLogEntity, Long> {

    boolean existsByEventId(String eventId);

    List<ActivityLogEntity> findByEmployeeNoOrderByOccurredAtDescIdDesc(String employeeNo, Pageable pageable);
}
