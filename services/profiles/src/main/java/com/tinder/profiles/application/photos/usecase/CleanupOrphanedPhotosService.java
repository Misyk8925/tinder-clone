package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoStoragePort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
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
    private final PhotoStoragePort storage;

    public void handle(String userId) {
        forProfile(owner.profileIdOf(userId));
    }

    public void forProfile(UUID profileId) {
        try {
            Set<String> catalogued = catalog.findForProfile(profileId).stream()
                    .map(StoredPhoto::s3Key)
                    .map(PhotoKeys::storageIdOf)
                    .collect(Collectors.toSet());

            Set<String> deleted = new HashSet<>();
            for (String key : storage.listKeys(PhotoKeys.profilePrefix(profileId))) {
                String storageId = PhotoKeys.storageIdOf(key);
                if (catalogued.contains(storageId) || !deleted.add(storageId)) {
                    continue;
                }
                log.info("Deleting orphaned photo {} of profile {}", storageId, profileId);
                PhotoKeys.allVariantKeys(profileId, storageId).forEach(storage::delete);
            }

            log.info("Cleanup completed for profile {}: deleted {} orphaned photo(s)",
                    profileId, deleted.size());
        } catch (Exception e) {
            log.error("Failed to clean up orphaned photos for profile {}", profileId, e);
        }
    }
}
