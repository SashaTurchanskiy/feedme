package com.shahbytes.feedme.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "feed.async")
public class FeedAsyncProperties {

    private boolean enabled;
    private final Stream stream = new Stream();
    private final Publisher publisher = new Publisher();
    private final Consumer consumer = new Consumer();


    @Getter
    @Setter
    public static class Stream{
        private String key;
    }

    @Getter
    @Setter
    public static class Publisher{
        private int batchSize;
        private int maxAttempts;
        private int fixedDelayMs;
    }

    @Getter
    @Setter
    private static class Consumer{
        private String group;
        private int batchSize;
        private String name;
        private int maxProcessingAttempts;
        private String dlqStreamKey;
        private boolean reclaimEnabled;
        private long reclaimFixedDelayMs;
        private long reclaimIdleMs;
        private long reclaimBatchSize;
        private long fixedDelayMs;
        private long recoverFixedDelayMs;
    }

}
