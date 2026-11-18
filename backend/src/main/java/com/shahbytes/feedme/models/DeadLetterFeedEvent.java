package com.shahbytes.feedme.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_feed_events")
@Data
@NoArgsConstructor
public class DeadLetterFeedEvent {

    @Id
    private String id;

    private String eventId;

    private String streamRecordId;

    private String payloadJson;

    private String failureReason;

    private int attempt;

    private Instant movedToDlqAt;

    public DeadLetterFeedEvent(int attempt, String failureReason, String payloadJson, String streamRecordId, String eventId){
        this.id = UUID.randomUUID().toString();
        this.attempt = attempt;
        this.failureReason = failureReason;
        this.payloadJson = payloadJson;
        this.streamRecordId = streamRecordId;
        this.eventId = eventId;
    }

    @PrePersist
    void onCreate(){
        this.movedToDlqAt = Instant.now();
    }
}
