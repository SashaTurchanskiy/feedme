package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import com.shahbytes.feedme.models.UserProfile;

public interface FeedService {
    TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit);

    UserProfile getUser(String userId);
}
