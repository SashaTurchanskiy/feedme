package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.dtos.OutboxEventStatus;
import com.shahbytes.feedme.models.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus status,
            Instant nextAttemptAt,
            Pageable pageable
    );
}
