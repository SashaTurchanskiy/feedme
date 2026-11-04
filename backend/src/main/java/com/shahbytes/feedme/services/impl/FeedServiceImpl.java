package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.FeedItemResponse;
import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import com.shahbytes.feedme.models.FollowRelation;
import com.shahbytes.feedme.models.UserProfile;
import com.shahbytes.feedme.repository.FollowRelationRepository;
import com.shahbytes.feedme.repository.UserProfileRepository;
import com.shahbytes.feedme.services.FeedMetricsService;
import com.shahbytes.feedme.services.FeedService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedServiceImpl implements FeedService {

    private final FeedMetricsService feedMetricsService;
    private final UserProfileRepository upRepository;
    private final FollowRelationRepository followRelationRepository;



    @Override
    public TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit) {
        long startedAtNanos = feedMetricsService.stratTime();
        int pageSize = normalizeLimit(limit);
        feedMetricsService.recordHomeFeedRequestedPageSize(limit, pageSize);

        try {
            UserProfile viewer = getUser(userId);
            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            VisibleAuthors visibleAuthors = getVisibleAuthors(viewer);

        }catch (ResponseStatusException ex){
            feedMetricsService.recordServiceError("get_home_feed", ex.getStatusCode().toString());
            throw ex;
        }
        return null;
    }

    private VisibleAuthors getVisibleAuthors(UserProfile viewer) {
        List<FollowRelation> followRelation = followRelationRepository.findByFollower_Id(viewer.getId());

        Set<String> allAuthorIds = new LinkedHashSet<>();
        Set<String> hotAuthorIds = new LinkedHashSet<>();
        Set<String> nonAuthorIds = new LinkedHashSet<>();

        allAuthorIds.add(viewer.getId());

        if (viewer.isHotUser()){
            hotAuthorIds.add(viewer.getId());
        }else {
            nonAuthorIds.add(viewer.getId());
        }

        for (FollowRelation relation : followRelation){
            UserProfile author = relation.getTargetUser();
            allAuthorIds.add(author.getId());
            if (author.isHotUser()){
                hotAuthorIds.add(author.getId());
            }else {
                nonAuthorIds.add(author.getId());
            }
        }
        return new VisibleAuthors(allAuthorIds, hotAuthorIds, nonAuthorIds);
    }

    @Override
    public UserProfile getUser(String userId) {
        return upRepository.findById(userId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.BAD_REQUEST,"User not found"));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0){
            return FeedCacheService.DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 20);
    }

    /*private record IdempotencyPostAttempt(Optional<PostCreationRequest> requestRecord,
                                           Optional<PostResponse> replayedRepose, boolean isProgress){}*/

    private record VisibleAuthors(Set<String> allAuthorIds, Set<String> hotAuthorIds, Set<String> nonHotAuthorIds){ }

    private record NormalFeedSliceResult(FeedSlice slice, String cacheOutcome){}

    private record FeedSlice(List<FeedItemResponse> items, boolean hasMore){}
}
