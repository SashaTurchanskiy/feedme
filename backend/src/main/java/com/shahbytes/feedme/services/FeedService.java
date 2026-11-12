package com.shahbytes.feedme.services;

import com.shahbytes.feedme.dtos.*;
import com.shahbytes.feedme.models.UserProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public interface FeedService {
    TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit);

    UserProfile getUser(String userId);

    TimeLinePageResponse getUserFeed(String userId, String cursor, int limit);

    List<UserProfileResponse> getUsers();

    FollowingResponse getFollowing(String followerId);

    FollowResponse follow(String followerId, String targetUserId);

    FollowResponse unfollow(String followerId, String targetUserId);

    PostResponse createPost(String authorId, String content, String idempotencyKey);
}
