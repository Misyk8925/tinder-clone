package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Issues a time-limited download URL for one variant of a caller's photo. */
@Service
@RequiredArgsConstructor
public class CreatePhotoDownloadUrlService {

    private final ProfilePhotoOwner owner;
    private final PhotoMediaPort media;

    public String handle(String userId, String storageId, String variant) {
        UUID profileId = owner.profileIdOf(userId);
        return media.presignedDownloadUrl(profileId, storageId, variant);
    }
}
