package com.tinder.profiles.photos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findAllByProfile_ProfileId(UUID profileId);
    List<Photo> findAllByProfile_ProfileIdOrderByPositionAsc(UUID profileId);
    int countByProfile_ProfileId(UUID profileId);
    Optional<Photo> findByS3Key(String s3Key);
    Optional<Photo> findByUrl(String url);
    Optional<Photo> findByPhotoIDAndProfile_ProfileId(UUID photoID, UUID profileId);
}
