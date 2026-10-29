package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.TimeLinePageResponse;

public interface FeedService {
    TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit);
}
