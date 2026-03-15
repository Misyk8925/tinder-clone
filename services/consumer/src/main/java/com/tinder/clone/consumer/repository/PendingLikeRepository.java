package com.tinder.clone.consumer.repository;

import com.tinder.clone.consumer.model.PendingLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PendingLikeRepository extends JpaRepository<PendingLike, UUID> {

    List<PendingLike> findByLikedUserIdOrderByLikedAtDesc(UUID likedUserId);

    @Modifying
    @Query("DELETE FROM PendingLike p WHERE p.likedUserId = :likedUserId AND p.likerProfileId = :likerProfileId")
    void deleteByPair(@Param("likedUserId") UUID likedUserId, @Param("likerProfileId") UUID likerProfileId);

    /**
     * Inserts a pending like, silently ignoring conflicts on (liked_user_id, liker_profile_id).
     * Avoids duplicate inserts without throwing an exception.
     */
    @Modifying
    @Query(value = """
        INSERT INTO pending_likes (id, liked_user_id, liker_profile_id, liked_at)
        VALUES (gen_random_uuid(), :likedUserId, :likerProfileId, :likedAt)
        ON CONFLICT (liked_user_id, liker_profile_id) DO NOTHING
        """, nativeQuery = true)
    void upsertIgnore(@Param("likedUserId") UUID likedUserId,
                      @Param("likerProfileId") UUID likerProfileId,
                      @Param("likedAt") Instant likedAt);
}
