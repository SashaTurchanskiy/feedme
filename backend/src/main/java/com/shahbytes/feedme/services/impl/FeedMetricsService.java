package com.shahbytes.feedme.services.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FeedMetricsService {

    private final MeterRegistry meterRegistry;

    public long startTime(){
        return System.nanoTime();
    }

    public void recordHomeFeedRequest(
        long startedAtNanos, String cacheOutcome, String mergeMode,
        boolean hasNextCursor, int itemsReturned
    ){
        Timer.builder("feedme.feed.home.latency")
                .description("Home feed request latency")
                .tag("cache_outcome", cacheOutcome)
                .tag("merge_mode", mergeMode)
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.feed.home.requests")
                .description("Home feed requests")
                .tag("cache_outcome", cacheOutcome)
                .tag("merge_mode", mergeMode)
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("feedme.feed.home.items_returned")
                .description("Number of items returned in home feed pages")
                .baseUnit("items")
                .register(meterRegistry)
                .record(itemsReturned);

        if (hasNextCursor){
            meterRegistry.counter("feedme.feed.home.next.coursor").increment();
        }
    }
    public void recordHomeFeedRequestedPageSize(int requestLimited, int normalizedPageSize){
        DistributionSummary.builder("feedme.feed.home.requested_page_size")
                .description("Requested and normalized home feed pages sizes")
                .baseUnit("items")
                .tag("requested_limit", String.valueOf(requestLimited))
                .register(meterRegistry)
                .record(normalizedPageSize);
    }

    public void recordUserFeedRequest(
            long startedAtNanos, boolean hasNextCursor, int itemsReturned
    ){
        Timer.builder("feedme.feed.user.latency")
                .description("User feed request latency")
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.feed.user.requests")
                .description("User feed request")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("feedme.feed.user.items_returned")
                .description("Number of items returned in user feed pages")
                .baseUnit("items")
                .register(meterRegistry)
                .record(itemsReturned);

        if (hasNextCursor){
            meterRegistry.counter("feedme.feed.user.next_corusor").increment();
        }
    }

    public void recordUserFeedRequestedPageSize(int requestLimited, int normalizedPageSize){
        DistributionSummary.builder("feedme.feed.user.requested_page_size")
                .description("Requested and normalized user feed pages sizes")
                .baseUnit("items")
                .tag("requested_limit", String.valueOf(requestLimited))
                .register(meterRegistry)
                .record(normalizedPageSize);
    }

    public void recordHomeFeedCacheLookup(String outcome) {
        meterRegistry.counter("feedme.feed.home.cache_lookup", "outcome", outcome).increment();
    }

    public void recordHomeFeedMerge(String mode, int baseItemsUsed, int hotItemUsed) {
        meterRegistry.counter("feedme.feed.home.merge", "mode", mode);

        meterRegistry.summary("feedme.feed.home.merge.base_items").record(baseItemsUsed);
        meterRegistry.summary("feedme.feed.home.merge.hot_items").record(hotItemUsed);
    }

    public void recordFollowRequest(long startedAtNanos, String action, boolean createdRelation){
        Timer.builder("feedme.follows.latency")
                .description("Follow and unfollow request latency")
                .tag("action", action)
                .tag("change_state", String.valueOf(createdRelation))
                .register(meterRegistry)
                .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        Counter.builder("feedme.follows.requests")
                .description("Fellow and unfollow requests")
                .tag("action", action)
                .tag("change_state", String.valueOf(createdRelation))
                .register(meterRegistry)
                .increment();
    }

    public void recordServiceError(String operation, String statusCode){
        Counter.builder("feedme.service.errors")
                .description("Service-level errors by operation and status code")
                .tag("operation", operation)
                .tag("status", statusCode)
                .register(meterRegistry)
                .increment();
    }

    public void recordDeliveryPath(String deliveryPath){
        meterRegistry.counter("feedme.feed.delivery.path", "path", deliveryPath).increment();
    }

    public void recordCacheMutation(String action){
        meterRegistry.counter("feedme.feed.cache.mutation", "action", action).increment();
    }


}
