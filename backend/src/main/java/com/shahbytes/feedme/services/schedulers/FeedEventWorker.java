package com.shahbytes.feedme.services.schedulers;

import com.shahbytes.feedme.config.FeedAsyncProperties;
import com.shahbytes.feedme.repository.DeadLetterFeedEventRepository;
import com.shahbytes.feedme.repository.FeedEventFailureRepository;
import com.shahbytes.feedme.repository.ProcessedFeedEventRepository;
import com.shahbytes.feedme.services.FeedService;
import com.shahbytes.feedme.services.impl.FeedMetricsService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

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
    }



