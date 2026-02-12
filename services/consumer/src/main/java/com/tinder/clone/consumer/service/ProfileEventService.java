package com.tinder.clone.consumer.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileEventService {

//    private final ProfileCacheRepository profileCacheRepository;
//
//    @Transactional
//    public void saveProfileCache (ProfileCreateEvent event) {
//        ProfileCacheModel profileCacheModel = ProfileCacheModel.builder()
//                .profileId(event.getProfileId())
//                .createdAt(event.getTimestamp())
//                .build();
//        log.info("Saving profile cache for profileId: {}", profileCacheModel.getProfileId());
//        profileCacheRepository.save(profileCacheModel);
//    }
//
//    @Transactional
//    public void deleteProfileCache(ProfileDeleteEvent event) {
//        UUID profileId = event.getProfileId();
//        log.info("Deleting profile cache for profileId: {}", profileId);
//
//        profileCacheRepository.findById(profileId).ifPresentOrElse(
//                profile -> {
//                    profileCacheRepository.delete(profile);
//                    log.info("Successfully deleted profile cache for profileId: {}", profileId);
//                },
//                () -> log.warn("Profile cache not found for profileId: {}", profileId)
//        );
//    }
}
