package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedCacheService {
    public static final int DEFAULT_PAGE_SIZE = 5;

    private static final Duration HOME_FEED_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    private final FeedMetricsService feedMetricsService;

    private final ObjectMapper objectMapper;

    public Optional<TimeLinePageResponse> getHomeFeed(String userId){
        try {
            String payload = redisTemplate.opsForValue().get(homeFeedKey(userId));

            if (payload == null){
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(payload, TimeLinePageResponse.class));
        }catch (Exception e){
            log.info("Something went wrong with redis while fetch userId {}, {}", userId, e.getMessage());
            return Optional.empty();
        }

    }

    private Object homeFeedKey(String userId) {
        return "feed:home:" + userId;
    }

    public void cacheHomeFeed(TimeLinePageResponse timeLinePageResponse){
        writeHomeFeed(timeLinePageResponse);
        feedMetricsService.recordCacheMutation("write_first_feed");
    }

    public void writeHomeFeed(TimeLinePageResponse timelinePageResponse) {
       try {
           String payload = objectMapper.writeValueAsString(timelinePageResponse);
           redisTemplate.opsForValue().set(
                   timelinePageResponse.timelineOwnerId(), payload, HOME_FEED_TTL
           );

       }catch (Exception e){
           log.info("Something went wrong with redis while caching userId {}, {}", timelinePageResponse.timelineOwnerId(), e.getMessage());
       }
    }
}
