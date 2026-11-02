package com.shahbytes.feedme.services;

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

    public long stratTime(){
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

    public void recordHomeFeedCacheLookup(String outcome){
        meterRegistry.counter("feedme.feed.home.cache_lookup", "outcome", outcome).increment();
    }

}
