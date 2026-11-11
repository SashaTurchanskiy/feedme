package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.FollowResponse;
import com.shahbytes.feedme.dtos.FollowingResponse;
import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import com.shahbytes.feedme.dtos.UserProfileResponse;
import com.shahbytes.feedme.models.UserProfile;

import java.util.List;

public interface FeedService {
    TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit);

    UserProfile getUser(String userId);

    TimeLinePageResponse getUserFeed(String userId, String cursor, int limit);

    List<UserProfileResponse> getUsers();

    FollowingResponse getFollowing(String followerId);

    FollowResponse follow(String followerId, String targetUserId);

    FollowResponse unfollow(String followerId, String targetUserId);
}
