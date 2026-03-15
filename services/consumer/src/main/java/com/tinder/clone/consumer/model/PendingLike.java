package com.tinder.clone.consumer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_likes", indexes = {
        @Index(name = "idx_pending_liked_user_id", columnList = "liked_user_id"),
        @Index(name = "idx_pending_liker_profile_id", columnList = "liker_profile_id"),
        @Index(name = "idx_pending_unique_pair", columnList = "liked_user_id, liker_profile_id", unique = true)
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PendingLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user who received the like — they can see this in "Likes You". */
    @Column(name = "liked_user_id", nullable = false)
    private UUID likedUserId;

    /** The user who sent the like. */
    @Column(name = "liker_profile_id", nullable = false)
    private UUID likerProfileId;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    @Column(name = "is_super", nullable = false)
    private boolean isSuper;
}
