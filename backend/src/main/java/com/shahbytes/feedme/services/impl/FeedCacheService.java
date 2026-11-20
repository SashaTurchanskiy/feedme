package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.FeedItemResponse;
import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    private String homeFeedKey(String userId) {
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


    public void evictHomeFeed(String userId){
      try {
          redisTemplate.delete(homeFeedKey(userId));
          feedMetricsService.recordCacheMutation("evict");
      }catch (Exception ex){
          ex.printStackTrace();
      }
    }


    public void prependToHomeFeed(String userId, FeedItemResponse item) {
        Optional<TimeLinePageResponse> cachedFeed = getHomeFeed(userId);
        if (cachedFeed.isEmpty()) {
            return;
        }

        TimeLinePageResponse existing = cachedFeed.get();

        List<FeedItemResponse> updatedItems = new ArrayList<>();
        updatedItems.add(item);

        existing.items().stream()
                .filter(existingItem -> !existingItem.postId().equals(item.postId()))
                .forEach(updatedItems::add);

        if (updatedItems.size() > DEFAULT_PAGE_SIZE) {
            updatedItems = updatedItems.subList(0, DEFAULT_PAGE_SIZE);
        }

        writeHomeFeed(new TimeLinePageResponse(
                existing.timelineOwnerId(),
                existing.mode(),
                existing.totalItems() + 1,
                updatedItems,
                existing.totalItems() + 1 > updatedItems.size()
                        && !updatedItems.isEmpty()
                        ? FeedCursorCodec.encode(updatedItems.get(updatedItems.size() - 1))
                        : null
        ));

        feedMetricsService.recordCacheMutation("prepend");

    }
}
