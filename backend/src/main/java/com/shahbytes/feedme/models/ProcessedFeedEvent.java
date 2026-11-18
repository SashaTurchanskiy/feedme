package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_feed_events")
@Data
@NoArgsConstructor
public class ProcessedFeedEvent {

    @Id
    private String eventId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedFeedEvent(String eventId){
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
