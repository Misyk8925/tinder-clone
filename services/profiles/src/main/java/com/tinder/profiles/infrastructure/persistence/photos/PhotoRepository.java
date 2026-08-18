package com.tinder.profiles.infrastructure.persistence.photos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findAllByProfile_ProfileId(UUID profileId);

    /**
     * Batch-loads the photos of several profiles in one query, for callers that
     * build shared profile snapshots from a flat projection (which cannot carry
     * a one-to-many). Grouping and ordering are the caller's job — see
     * {@link SharedPhotoMapper#byProfileId}.
     */
    List<Photo> findAllByProfile_ProfileIdIn(Collection<UUID> profileIds);
    List<Photo> findAllByProfile_ProfileIdOrderByPositionAsc(UUID profileId);
    int countByProfile_ProfileId(UUID profileId);
    Optional<Photo> findByS3Key(String s3Key);
    Optional<Photo> findByUrl(String url);
    Optional<Photo> findByPhotoIDAndProfile_ProfileId(UUID photoID, UUID profileId);
}
