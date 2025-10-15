package com.tinder.profiles.photos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findAllByProfile_ProfileId(UUID userId);
    int countByProfile_ProfileId(UUID userId);
    Optional<Photo> findByS3Key(String s3Key);
    Optional<Photo> findByUrl(String url);
}