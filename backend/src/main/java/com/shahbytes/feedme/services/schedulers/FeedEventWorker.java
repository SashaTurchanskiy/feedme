package com.shahbytes.feedme.services.schedulers;

import com.shahbytes.feedme.config.FeedAsyncProperties;
import com.shahbytes.feedme.models.DeadLetterFeedEvent;
import com.shahbytes.feedme.models.FeedEventFailure;
import com.shahbytes.feedme.models.ProcessedFeedEvent;
import com.shahbytes.feedme.repository.DeadLetterFeedEventRepository;
import com.shahbytes.feedme.repository.FeedEventFailureRepository;
import com.shahbytes.feedme.repository.ProcessedFeedEventRepository;
import com.shahbytes.feedme.services.FeedService;
import com.shahbytes.feedme.services.impl.FeedMetricsService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnBooleanProperty(value = "feed.async.enabled", havingValue = true, matchIfMissing = true)
@RequiredArgsConstructor
@Getter
public class FeedEventWorker {


    @PostConstruct
    public void initializeConsumerGroup() {
        validateConfiguration();
        String streamKey = properties.getStream().getKey();
        String consumerGroupName = properties.getConsumer().getGroup();
        try {

            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream()
                    .groups(streamKey);

            boolean exists = groups.stream()
                    .anyMatch(group ->
                            consumerGroupName.equals(group.groupName()));

            if (!exists) {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroupName);
            }
        } catch (RedisSystemException exception) {
            if (isMissingStream(exception)) {
                createConsumerGroupWithStream(streamKey, consumerGroupName);
                return;
            }
            throw exception;
        }
    }

    private void validateConfiguration() {
        requiredText(properties.getStream().getKey(), "feed.async.strea.key");
    }

    private void requiredText(String value, String propertyName){
        if (value == null || value.isBlank()){
            throw new IllegalStateException(propertyName = "must not be blank");
        }
    }

    private void createConsumerGroupWithStream(String streamKey, String consumerGroupName) {
        RecordId initializeRecordId = redisTemplate.opsForStream().add(
                MapRecord.create(streamKey, Map.of("__system__", "stream-init"))
        );

        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroupName);
        } catch (RedisSystemException ex) {
            if (!groupAlreadyExist(ex)) {
                throw ex;
            }

        } finally {
            if (initializeRecordId != null) {
                redisTemplate.opsForStream().delete(streamKey, initializeRecordId);
            }
        }
    }

    private boolean groupAlreadyExist(RedisSystemException ex) {
        Throwable cause = ex.getCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("BUSYGROUP");
    }

    private boolean isMissingStream(RedisSystemException exception) {
        Throwable cause = exception.getCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("ERR no such key");
    }


    private final StringRedisTemplate redisTemplate;
    private final ProcessedFeedEventRepository processedFeedEventRepository;
    private final DeadLetterFeedEventRepository deadLetterFeedEventRepository;
    private final FeedEventFailureRepository feedEventFailureRepository;
    private final ObjectMapper objectMapper;

    private final FeedService feedService;
    private final FeedMetricsService feedMetricsService;

    private final FeedAsyncProperties properties;


    @Scheduled(fixedDelayString = "${feed.async.consumer.fixed-delay-ms}")
    @Transactional
    public void consumePostEvents() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(
                            properties.getConsumer().getGroup(),
                            properties.getConsumer().getName()
                    ),
                    StreamReadOptions.empty().count(properties.getConsumer().getBatchSize()),
                    StreamOffset.create(properties.getStream().getKey(),
                            ReadOffset.lastConsumed())
            );
        } catch (Exception ex) {
            feedMetricsService.recordServiceError(
                    "consume_post_event",
                    "REDIS_READ_ERROR"
            );
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            processRecord(record, "consume_post_event");
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record, String operationName) {
        String streamKey = properties.getStream().getKey();
        String consumeGroup = properties.getConsumer().getGroup();

        Map<Object, Object> eventFields = record.getValue();

        String eventId = (String) eventFields.get("eventId");
        String postId = (String) eventFields.get("postId");

        if (eventId == null || postId == null){
            redisTemplate.opsForStream().acknowledge(streamKey, consumeGroup, record.getId());
            feedMetricsService.recordServiceError(operationName, "MALFORMED_EVENT");
            return;
        }
        try {
            try {
                processedFeedEventRepository.saveAndFlush(new ProcessedFeedEvent(eventId));
            }catch (DataIntegrityViolationException duplicateEvent){
                redisTemplate.opsForStream().acknowledge(streamKey, consumeGroup, record.getId());
                return;
            }

            feedService.processPostCreatedEvent(postId);
            clearFailureState(eventId);

            redisTemplate.opsForStream().acknowledge(streamKey, consumeGroup, record.getId());
        }catch (Exception ex){
            int attemptCount = recordFailureAttempt(eventId, ex);
            feedMetricsService.recordServiceError(operationName, "PROCESSING_ERROR");

            if (attemptCount < properties.getConsumer().getMaxProcessingAttempts()){
                feedMetricsService.recordAsyncWorkedRetry(operationName);
                return;
            }
            moveToDeadLetter(record, eventFields, eventId, attemptCount, ex, operationName);
            redisTemplate.opsForStream().acknowledge(streamKey, consumeGroup, record.getId());
            feedEventFailureRepository.deleteById(eventId);
            feedMetricsService.recordAsyncWorkerDeadLetter(operationName);
        }

    }

    private void moveToDeadLetter(MapRecord<String, Object, Object> record,
                                  Map<Object, Object> eventFields, String eventId,
                                  int attemptCount, Exception ex, String operationName) {
        String payloadJson = toJson(eventFields);
        String failureReason = sanitizeErrorMessage(ex);

        deadLetterFeedEventRepository.save(new DeadLetterFeedEvent(attemptCount,
                failureReason, payloadJson, record.getId().getValue(),
                eventId));

        try {
            redisTemplate.opsForStream().add(MapRecord.create(
                    properties.getConsumer().getDlqStreamKey(),
                    Map.of(
                            "eventId", eventId,
                            "sourceRecordId", record.getId().getValue(),
                            "failureReason", failureReason,
                            "attemptCount", String.valueOf(attemptCount),
                            "payloadjson", payloadJson
                    )
            ));
        }catch (Exception redisException){
            feedMetricsService.recordServiceError(
                    operationName,
                    "DLQ_STREAM_PUBLISH_ERROR"
            );
        }
    }

    private String toJson(Map<Object, Object> eventFields) {
        try {
            return objectMapper.writeValueAsString(eventFields);
        }catch (Exception e){
            return "{\"serializationError\":true}";
        }
    }

    private int recordFailureAttempt(String eventId, Exception ex) {
        FeedEventFailure failure = feedEventFailureRepository.findById(eventId)
                .orElseGet(()-> new FeedEventFailure(eventId));

        int attemptCount = failure.recordFailure(sanitizeErrorMessage(ex));
        feedEventFailureRepository.saveAndFlush(failure);
        return attemptCount;
    }

    private String sanitizeErrorMessage(Exception ex) {
        String rawMessage = ex.getMessage() == null ? ex.getClass().getSimpleName()
                : ex.getMessage();
        return rawMessage.length() > 1000 ? rawMessage.substring(0, 1000) : rawMessage;
    }

    private void clearFailureState(String eventId) {
        feedEventFailureRepository.deleteById(eventId);
    }


}



