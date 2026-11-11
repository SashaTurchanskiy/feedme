package com.shahbytes.feedme.dtos;

import java.util.List;

public record FollowingResponse(
        String followerId,
        List<String> targetUserId,
        int totalFollowing
) {
}
