package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
 * <p>{@link Page}/{@link Pageable} are a deliberate, framework-neutral
 * concession at this boundary (paging is a query concern the use cases need);
 * everything else is pure domain/JDK types.
 */
public interface ProfileRepositoryPort {

    Optional<Profile> findById(UUID id);

    Optional<Profile> findByUserId(String userId);

    Optional<Profile> findByName(String name);

    /** Profile id of the user's non-deleted profile, if one exists. */
    Optional<UUID> findActiveProfileIdByUserId(String userId);

    Page<Profile> findAll(Pageable pageable);

    List<Profile> findAllById(Collection<UUID> ids);

    Profile save(Profile profile);

    void deleteAllById(Collection<UUID> ids);
}
