package com.tinder.profiles.outbox;

import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProfileEventOutboxRepository extends JpaRepository<ProfileEventOutbox, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM profile_event_outbox
                    WHERE published_at IS NULL
                      AND dead_lettered_at IS NULL
                      AND next_attempt_at <= :now
                    ORDER BY created_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<ProfileEventOutbox> lockNextBatchForPublish(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
