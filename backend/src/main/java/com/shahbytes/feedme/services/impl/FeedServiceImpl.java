package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.*;
import com.shahbytes.feedme.models.FollowRelation;
import com.shahbytes.feedme.models.FollowRelationId;
import com.shahbytes.feedme.models.Post;
import com.shahbytes.feedme.models.UserProfile;
import com.shahbytes.feedme.repository.FollowRelationRepository;
import com.shahbytes.feedme.repository.PostRepository;
import com.shahbytes.feedme.repository.UserProfileRepository;
import com.shahbytes.feedme.services.FeedService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedServiceImpl implements FeedService {

    private static final Comparator<FeedItemResponse> FEED_ORDER
            = Comparator.comparing(FeedItemResponse::createdAt)
            .thenComparing(FeedItemResponse::postId)
            .reversed();

    private final FeedMetricsService feedMetricsService;
    private final UserProfileRepository upRepository;
    private final FollowRelationRepository followRelationRepository;
    private final FeedCacheService feedCacheService;
    private final PostRepository postRepository;


    @Override
    public TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit) {
        long startedAtNanos = feedMetricsService.startTime();
        int pageSize = normalizeLimit(limit);
        feedMetricsService.recordHomeFeedRequestedPageSize(limit, pageSize);

        try {
            UserProfile viewer = getUser(userId);
            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            VisibleAuthors visibleAuthors = getVisibleAuthors(viewer);

            NormalFeedSliceResult normalFeedSliceResult =
                    getNormalHomeFeedSlice(viewer.getId(), visibleAuthors.nonHotAuthorIds,
                            pageCursor, pageSize);

            FeedSlice hotSlice = getHotHomeFeedSlice(viewer.getId(), visibleAuthors.hotAuthorIds, pageCursor, pageSize);

            int totalItems = Math.toIntExact(postRepository.countByAuthor_IdIn(visibleAuthors.allAuthorIds));

            TimeLinePageResponse response = mergeHomeFeedSlices(userId, totalItems, pageSize,
                    normalFeedSliceResult.slice, hotSlice);

            feedMetricsService.recordHomeFeedRequest(startedAtNanos, normalFeedSliceResult.cacheOutcome,
                    determineMergeMode(normalFeedSliceResult.slice, hotSlice),
                    response.nextCursor() != null,
                    response.items().size()
            );
            return response;

        }catch (ResponseStatusException ex){
            feedMetricsService.recordServiceError("get_home_feed", ex.getStatusCode().toString());
            throw ex;
        }
    }

    private TimeLinePageResponse mergeHomeFeedSlices(String userId, int totalItems, int pageSize,
                                                     FeedSlice normalSlice,
                                                     FeedSlice hotSlice) {

        List<FeedItemResponse> mergeItems = new ArrayList<>(pageSize + 1);

        int normalIndex = 0;
        int hotIndex = 0;
        int normalItemUsed = 0;
        int hotItemUsed = 0;

        while (mergeItems.size() < pageSize + 1
        && (normalIndex < normalSlice.items().size() ||
                hotIndex < hotSlice.items.size())
        ){

            FeedItemResponse nextNormal = normalIndex < normalSlice.items().size()
                    ? normalSlice.items().get(normalIndex) : null;

            FeedItemResponse nextHot = hotIndex < hotSlice.items().size()
                    ? hotSlice.items().get(hotIndex) : null;

             /*
            compare(a, b)
            returns:
            - -ve => a should come before b
            -   0 => they are equal in sort order
            - +ve => a should come after b

             */

            if (nextHot == null || (nextNormal != null && FEED_ORDER.compare(nextNormal, nextHot) < 0)){
                mergeItems.add(nextNormal);
                normalIndex++;
                normalItemUsed++;
            }else {
                mergeItems.add(nextHot);
                hotIndex++;
                hotItemUsed++;
            }
        }

        boolean hasMore = mergeItems.size() > pageSize
                || normalIndex < normalSlice.items().size()
                || hotIndex < hotSlice.items().size()
                || normalSlice.hasMore()
                || hotSlice.hasMore();

        List<FeedItemResponse> pageItem = mergeItems.size() > pageSize ? mergeItems.subList(0, pageSize)
                : mergeItems;

        String nextCursor = hasMore && !pageItem.isEmpty()
                ? FeedCursorCodec.encode(pageItem.get(pageItem.size() -1))
                : null;

        feedMetricsService.recordHomeFeedMerge(determineMergeMode(normalSlice, hotSlice),
                normalItemUsed, hotItemUsed);

        return new TimeLinePageResponse(
                userId,
                TimelineMode.HOME,
                totalItems,
                pageItem,
                nextCursor
        );

    }

    private String determineMergeMode(FeedSlice normalSlice, FeedSlice hotSlice) {
        boolean hasNormal = !normalSlice.items().isEmpty();
        boolean hasHot = !hotSlice.items().isEmpty();

        if (hasNormal && hasHot){
            return "mixed";
        }
        if (hasNormal){
            return "normal_only";
        }
        if (hasHot){
            return "hot_only";
        }
        return "empty";
    }

    private FeedSlice getHotHomeFeedSlice(String userId, Set<String> hotAuthorIds,
                                          FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (hotAuthorIds.isEmpty()){
            return new FeedSlice(List.of(), false);
        }

        List<Post> posts = fetchHomeFeedPosts(hotAuthorIds, pageCursor, pageSize +1);

        return buildFeedSlice(posts, pageSize, userId, hotAuthorIds);

    }

    private NormalFeedSliceResult getNormalHomeFeedSlice(String userId, Set<String> nonHotAuthorIds,
                                                         FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (nonHotAuthorIds.isEmpty()){
            FeedSlice slice = new FeedSlice(List.of(), false);
            return new NormalFeedSliceResult(slice, "empty_non_hot");
        }

        Optional<FeedSlice> cachedSLice = getCachedNormalHomeFeedSlice(userId, pageCursor, pageSize);

        if (cachedSLice.isPresent()){
            return new NormalFeedSliceResult(cachedSLice.get(), "hit");
        }

        List<Post> posts = fetchHomeFeedPosts(nonHotAuthorIds, pageCursor, pageSize +1);

        FeedSlice slice = buildFeedSlice(posts, pageSize, userId, nonHotAuthorIds);

        if (pageCursor == null && pageSize == FeedCacheService.DEFAULT_PAGE_SIZE){
            TimeLinePageResponse tpResponse = new TimeLinePageResponse(
                    userId,
                    TimelineMode.HOME,
                    Math.toIntExact(postRepository.countByAuthor_IdIn(nonHotAuthorIds)),
                    slice.items,
                    slice.hasMore && !slice.items.isEmpty()
                    ? FeedCursorCodec.encode(
                            slice.items().get(slice.items().size() -1)
                    )
                            :null
            );

            feedCacheService.cacheHomeFeed(tpResponse);
        }
        return new NormalFeedSliceResult(slice, "miss");


    }

    private FeedSlice buildFeedSlice(List<Post> posts, int pageSize, String userId,
                                     Set<String> visibleAuthorIds) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePosts = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePosts.stream()
                .map(post -> toFeedItem(post, userId, visibleAuthorIds))
                .toList();
        return new FeedSlice(pageItems, hasMore);

    }

    private FeedItemResponse toFeedItem(Post post, String viewerId, Set<String> visibleAuthorIds) {
         /*
           - post age = 120 minutes

           - recency divisor = 600 minutes = 10 hours
           at 0 minutes old , recency Score 1.0
           at 300 minutes, rs => 0.5
           at 600 minutes, rs => 0.0

           - hot users penalty = 0.15
           - followed-author boost = 0.2
           - rounding multiplier = 100

           Example:
           normal user

           120 minutes
           120/600 = 0.2
           rs = 1.0 - 0.2 = 0.8;

           hot-user penalty = 0.0

           0.2 => affinity boost

           raw score => 0.8 + 0.2 - 0.0 = 1.0
         */

        UserProfile author = post.getAuthor();

        double freshnessWindow = 600.0;
        double recencyScore = 1.0 - Duration.between(
                post.getCreatedAt(), Instant.now()).toMinutes() / freshnessWindow;

        double hotUserPenalty = author.isHotUser() ? 0.15 : 0.0;
        double affinityBoost = visibleAuthorIds.contains(author.getId())
                && viewerId.equals(author.getId()) ? 0.0 : 0.2;

        double rankingScore = Math.round((recencyScore + affinityBoost - hotUserPenalty) * 100.0) / 100.0;
        /*
        raw score = 0.856 * 100 - 85.6 -> round -> 86 -> 0.86
         */
        String deliveryStrategy = author.isHotUser()
                ? "hybrid-pull" : "fan-out-om.write";

        String rankingReason = author.isHotUser()
                ? "Hot-user content is blended with a pull-based bias to reduce write amplification"
                : "Recent Content from followed accounts is promoted for freshness and affinity";

        return new FeedItemResponse(
                post.getId(),
                author.getId(),
                author.getHandle(),
                author.getName(),
                post.getContent(),
                post.getCreatedAt(),
                rankingScore,
                deliveryStrategy,
                rankingReason
        );

    }

    private List<Post> fetchHomeFeedPosts(Set<String> authorIds, FeedCursorCodec.FeedCursor pageCursor,
                                          int fetchSize) {

        PageRequest pageRequest = PageRequest.of(0, fetchSize);
        if (pageCursor == null){
            return postRepository.findByAuthor_IdInOrderByCreatedAtDescIdDesc(authorIds, pageRequest);
        }
        return postRepository.findHomeFeedPageAfterCursor(
                authorIds, pageCursor.createdAt(), pageCursor.postId(), pageRequest);
    }

    private Optional<FeedSlice> getCachedNormalHomeFeedSlice(String userId,
                                                             FeedCursorCodec.FeedCursor pageCursor, int pageSize) {
        if (pageCursor != null){
            feedMetricsService.recordHomeFeedCacheLookup("bypass_cursor");
            return Optional.empty();
        }

        if (pageSize > FeedCacheService.DEFAULT_PAGE_SIZE){
            feedMetricsService.recordHomeFeedCacheLookup("bypass_page_size");
            return Optional.empty();
        }

        Optional<TimeLinePageResponse> cachedPage = feedCacheService.getHomeFeed(userId);

        if (cachedPage.isEmpty()){
            feedMetricsService.recordHomeFeedCacheLookup("miss");
            return Optional.empty();
        }

        Optional<FeedSlice> adapted = adaptCachedFirstPage(cachedPage.get(), pageSize);
        feedMetricsService.recordHomeFeedCacheLookup(adapted.isPresent() ? "hit" : "incomplete");
        return adapted;
    }

    private Optional<FeedSlice> adaptCachedFirstPage(TimeLinePageResponse cachePage, int pageSize) {
        if (cachePage.items().size() < pageSize && cachePage.nextCursor() != null){
            return Optional.empty();
        }

        List<FeedItemResponse> pageItems = cachePage.items()
                .stream()
                .limit(pageSize)
                .toList();

        FeedSlice slice = new FeedSlice(pageItems, cachePage.totalItems() > pageItems.size());
        return Optional.of(slice);
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

    @Override
    public TimeLinePageResponse getUserFeed(String userId, String cursor, int limit) {
        long startedAtNanos = feedMetricsService.startTime();
        int pageSize = normalizeLimit(limit);

        feedMetricsService.recordUserFeedRequestedPageSize(limit, pageSize);

        try {
            getUser(userId);

            FeedCursorCodec.FeedCursor pageCursor = FeedCursorCodec.parse(cursor);
            List<Post> posts = fetchUserFeedPosts(userId, pageCursor, pageSize + 1);
            int totalItems = Math.toIntExact(postRepository.countByAuthorId(userId));
            TimeLinePageResponse response = buildTimelinePage(
                    userId, TimelineMode.USER, posts, totalItems, pageSize
            );

            feedMetricsService.recordUserFeedRequest(startedAtNanos,
                    response.nextCursor() != null, response.items().size());
            return response;

        }catch (ResponseStatusException ex){
            feedMetricsService.recordServiceError(
                    "get_user_feed",
                    ex.getStatusCode().toString()
            );
            throw ex;
        }
    }

    @Override
    public List<UserProfileResponse> getUsers() {
        return upRepository.findAll(Sort.by("id")).stream()
                .map(this::toUserProfileResponse)
                .toList();
    }

    @Override
    public FollowingResponse getFollowing(String followerId) {
        getUser(followerId);
        List<String> targetUserIds = followRelationRepository.findByFollower_Id(followerId).stream()
                .map(relation -> relation.getTargetUser().getId())
                .toList();
        return new FollowingResponse(followerId, targetUserIds, targetUserIds.size());
    }

    @Override
    @Transactional
    public FollowResponse follow(String followerId, String targetUserId) {
        long startedAtNano = feedMetricsService.startTime();
        try {
            validateFollowingRequest(followerId, targetUserId);

            UserProfile follower = getUser(followerId);
            UserProfile target = getUser(targetUserId);
            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);

            boolean createdRelation = false;

            if (!followRelationRepository.existsById(relationId)){
                followRelationRepository.save(new FollowRelation(follower, target));
                createdRelation = true;
            }

            feedCacheService.evictHomeFeed(followerId);
            feedMetricsService.recordFollowRequest(startedAtNano, "follow", createdRelation);
            return new FollowResponse(followerId, targetUserId,
                    true,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    )
            );
        }catch (ResponseStatusException ex){
            feedMetricsService.recordServiceError(
                    "follow", ex.getStatusCode().toString()
            );
            throw ex;
        }
    }

    @Override
    @Transactional
    public FollowResponse unfollow(String followerId, String targetUserId) {
        long startedAtNanos = feedMetricsService.startTime();

        try {
            validateFollowingRequest(followerId, targetUserId);
            FollowRelationId relationId = new FollowRelationId(followerId, targetUserId);
            boolean relationExisted = followRelationRepository.existsById(relationId);

            followRelationRepository.deleteById(relationId);
            feedCacheService.evictHomeFeed(followerId);
            feedMetricsService.recordFollowRequest(startedAtNanos, "unfollow", relationExisted);

            return new FollowResponse(followerId, targetUserId, false,
                    Math.toIntExact(
                            followRelationRepository.countByFollower_Id(followerId)
                    ));
        }catch (ResponseStatusException ex){
            feedMetricsService.recordServiceError("unfollow", ex.getStatusCode().toString());
            throw ex;
        }
    }

    private void validateFollowingRequest(String followerId, String targetUserId) {
        if (followerId.equals(targetUserId)){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User cannot follow themselves"
            );
        }
        getUser(followerId);
        getUser(targetUserId);
    }

    private UserProfileResponse toUserProfileResponse(UserProfile user) {
        return new UserProfileResponse(
                user.getId(),
                user.getHandle(),
                user.getName(),
                user.getBio(),
                user.isHotUser()
        );
    }

    private TimeLinePageResponse buildTimelinePage(String timelineOwnerId, TimelineMode mode,
                                                   List<Post> posts, int totalItems, int pageSize) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> pagePost = hasMore ? posts.subList(0, pageSize) : posts;

        List<FeedItemResponse> pageItems = pagePost.stream()
                .map(post -> toFeedItem(post, timelineOwnerId, Set.of(timelineOwnerId)))
                .toList();

        String nextCursor = hasMore && !pageItems.isEmpty()
                ? FeedCursorCodec.encode(pageItems.get(pageItems.size() -1))
                : null;

        return new TimeLinePageResponse(timelineOwnerId, mode, totalItems, pageItems, nextCursor);
    }

    private List<Post> fetchUserFeedPosts(String userId, FeedCursorCodec.FeedCursor pageCursor, int fetchSize){
        PageRequest pageRequest = PageRequest.of(0, fetchSize);
        if (pageCursor == null){
            return postRepository.findByAuthor_IdOrderByCreatedAtDescIdDesc(userId, pageRequest);
        }

        return postRepository.findUserFeedPageAfterCursor(userId, pageCursor.createdAt(), pageCursor.postId(),
        pageRequest);
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
