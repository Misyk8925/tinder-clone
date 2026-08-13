package com.tinder.profiles.infrastructure.persistence.backfill;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeckCardProjectionBackfillRunRepository
        extends JpaRepository<DeckCardProjectionBackfillRunJpaEntity, UUID> {

    boolean existsByStatus(DeckCardProjectionBackfillStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeckCardProjectionBackfillRunJpaEntity r WHERE r.runId = :runId")
    Optional<DeckCardProjectionBackfillRunJpaEntity> findByIdForUpdate(@Param("runId") UUID runId);
}
