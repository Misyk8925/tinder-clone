package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.command.UploadPhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.ImageDimensions;
import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.PhotoVariants;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.model.UploadedPhoto;
import com.tinder.profiles.application.photos.port.out.ImageVariantsPort;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoStoragePort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.PhotoPolicy;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Puts an image into one of a profile's photo slots: validates it against the
 * {@link PhotoPolicy}, renders the variants, stores them and catalogues the
 * original. Uploading onto an occupied slot replaces what was there.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadPhotoService {

    private final ProfilePhotoOwner owner;
    private final PhotoCatalogPort catalog;
    private final PhotoStoragePort storage;
    private final ImageVariantsPort images;
    private final CleanupOrphanedPhotosService cleanupOrphaned;
    private final PhotoPolicy policy;

    @Transactional
    public UploadedPhoto handle(UploadPhotoCommand cmd) {
        UUID profileId = owner.profileIdOf(cmd.userId());

        policy.requireAllowedContentType(cmd.contentType());
        policy.requireWithinSizeLimit(cmd.size());

        List<StoredPhoto> existing = catalog.findForProfile(profileId);
        // Preserved behaviour: a full album is rejected outright, even for a replacement.
        policy.requireFreeSlot(existing.size());
        policy.requireAssignablePosition(cmd.position(), existing.size());
        if (existing.isEmpty() && cmd.position() != 0) {
            throw new PhotoValidationException("Invalid position: " + cmd.position());
        }

        if (cmd.position() < existing.size()) {
            replaceSlot(profileId, existing.get(cmd.position()));
        }

        cleanupOrphaned.forProfile(profileId);

        ImageDimensions dimensions = images.probe(cmd.image())
                .orElseThrow(() -> new PhotoValidationException("Corrupted image"));
        policy.requireWithinDimensionLimits(dimensions);

        PhotoVariants variants = images.render(cmd.image());
        String storageId = UUID.randomUUID().toString();
        PhotoKeys.VARIANTS.forEach(variant -> storage.put(
                PhotoKeys.variantKey(profileId, storageId, variant),
                variants.of(variant),
                "image/jpeg"));
        log.info("Stored {} variants of photo {} for profile {}",
                PhotoKeys.VARIANTS.size(), storageId, profileId);

        String originalKey = PhotoKeys.variantKey(profileId, storageId, "original");
        catalog.save(new PhotoDraft(
                profileId,
                originalKey,
                cmd.position() == 0,
                cmd.position(),
                storage.publicUrl(originalKey),
                "image/jpeg",
                variants.original().length));

        return new UploadedPhoto(
                storageId,
                storage.publicUrl(originalKey),
                storage.publicUrl(PhotoKeys.variantKey(profileId, storageId, "large")),
                storage.publicUrl(PhotoKeys.variantKey(profileId, storageId, "medium")),
                storage.publicUrl(PhotoKeys.variantKey(profileId, storageId, "small")));
    }

    private void replaceSlot(UUID profileId, StoredPhoto occupant) {
        log.debug("Replacing photo at position {} (photoId {}, key {})",
                occupant.position(), occupant.photoId(), occupant.s3Key());

        String storageId = PhotoKeys.storageIdOf(occupant.s3Key());
        PhotoKeys.allVariantKeys(profileId, storageId).forEach(storage::delete);
        catalog.deleteById(occupant.photoId());
    }
}
