package com.tinder.profiles.photos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findAllByProfileId(UUID userId);
    int countByProfileId(UUID userId);
    Optional<Photo> findByS3Key(String s3Key);
    Optional<Photo> findByUrl(String url);
}