package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deletes stored objects a profile no longer has a catalogue entry for — the
 * residue of interrupted uploads.
 *
 * <p>Failures are logged and swallowed on purpose: cleanup runs alongside the
 * upload path and must never fail the caller's request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupOrphanedPhotosService {

    private final ProfilePhotoOwner owner;
    private final PhotoCatalogPort catalog;
    private final PhotoMediaPort media;

    public void handle(String userId) {
        forProfile(owner.profileIdOf(userId));
    }

    public void forProfile(UUID profileId) {
        try {
            Set<String> catalogued = catalog.findForProfile(profileId).stream()
                    .map(StoredPhoto::s3Key)
                    .map(PhotoKeys::storageIdOf)
                    .collect(Collectors.toSet());
            int deleted = media.cleanupOrphans(profileId, catalogued);
            log.info("Cleanup completed for profile {}: deleted {} orphaned photo(s)",
                    profileId, deleted);
        } catch (Exception e) {
            log.error("Failed to clean up orphaned photos for profile {}", profileId, e);
        }
    }
}
