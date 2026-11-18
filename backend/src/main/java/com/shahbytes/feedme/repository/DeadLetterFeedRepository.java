package com.shahbytes.feedme.repository;

import com.shahbytes.feedme.models.DeadLetterFeedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterFeedRepository extends JpaRepository<DeadLetterFeedEvent, String> {
}
