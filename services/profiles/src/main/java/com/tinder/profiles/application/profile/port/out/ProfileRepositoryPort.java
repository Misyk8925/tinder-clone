package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.Profile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying the {@link Profile} aggregate.
 *
 * <p>Expressed entirely in domain terms — the implementing adapter
 * ({@code infrastructure.profile.persistence.JpaProfileRepositoryAdapter}) owns
 * the translation to and from the JPA entity. The application layer depends on
 * this interface, never on Spring Data repositories directly.
 *
 * <p>Every signature is expressed in domain/JDK types only — no Spring Data
 * types leak through this boundary.
 */
public interface ProfileRepositoryPort {

    Optional<Profile> findByUserId(String userId);

    List<Profile> findAllById(Collection<UUID> ids);

    /** Premium profiles whose paid period ended before {@code asOf}. */
    List<Profile> findExpiredPremium(LocalDateTime asOf);

    /** Ids of profiles soft-deleted before {@code cutoff}, eligible for purging. */
    List<UUID> findSoftDeletedBefore(LocalDateTime cutoff);

    Profile save(Profile profile);

    void deleteAllById(Collection<UUID> ids);
}
