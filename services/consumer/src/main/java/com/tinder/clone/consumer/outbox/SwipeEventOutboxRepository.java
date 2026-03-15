package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.outbox.model.SwipeEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SwipeEventOutboxRepository extends JpaRepository<SwipeEventOutbox, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM swipe_event_outbox
                    WHERE published_at IS NULL
                      AND dead_lettered_at IS NULL
                      AND next_attempt_at <= :now
                    ORDER BY created_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<SwipeEventOutbox> lockNextBatchForPublish(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}
