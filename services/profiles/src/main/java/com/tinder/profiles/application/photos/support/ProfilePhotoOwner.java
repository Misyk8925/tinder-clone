package com.tinder.profiles.application.photos.support;

import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the profile that owns a caller's photos. Soft-deleted profiles are
 * treated as absent, so photos cannot be added to or read from them.
 */
@Component
@RequiredArgsConstructor
public class ProfilePhotoOwner {

    private final ProfileRepositoryPort profiles;

    public UUID profileIdOf(String userId) {
        return profiles.findByUserId(userId)
                .filter(profile -> !profile.isDeleted())
                .map(Profile::getId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));
    }
}
