package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "follow_relation")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowRelation {

    @EmbeddedId
    private FollowRelationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("followerId")
    @JoinColumn(name = "follower_Id", nullable = false)
    private UserProfile follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("targetUserId")
    @JoinColumn(name = "target_user_id", nullable = false)
    private UserProfile targetUser;

    public FollowRelation(UserProfile follower, UserProfile targetUser) {
        this.id = new FollowRelationId(follower.getId(), targetUser.getId());
        this.follower = follower;
        this.targetUser = targetUser;
    }
}
