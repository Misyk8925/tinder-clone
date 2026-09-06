package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.command.UploadPhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.model.StoredPhotoMedia;
import com.tinder.profiles.application.photos.model.UploadedPhoto;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.PhotoPolicy;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Puts an image into one of a profile's photo slots. Slot rules stay here;
 * image validation, variants and object storage are delegated to the photos
 * service via {@link PhotoMediaPort}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadPhotoService {

    private final ProfilePhotoOwner owner;
    private final PhotoCatalogPort catalog;
    private final PhotoMediaPort media;
    private final CleanupOrphanedPhotosService cleanupOrphaned;
    private final PhotoPolicy policy;
    private final DomainEventPublisherPort events;

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

        StoredPhotoMedia stored = media.store(profileId, cmd.image(), cmd.contentType());
        log.info("Stored photo {} for profile {}", stored.storageId(), profileId);

        catalog.save(new PhotoDraft(
                profileId,
                stored.originalKey(),
                cmd.position() == 0,
                cmd.position(),
                stored.originalUrl(),
                stored.contentType(),
                stored.size()));

        events.publishCardChanged(profileId);

        return new UploadedPhoto(
                stored.storageId(),
                stored.originalUrl(),
                stored.largeUrl(),
                stored.mediumUrl(),
                stored.smallUrl());
    }

    private void replaceSlot(UUID profileId, StoredPhoto occupant) {
        log.debug("Replacing photo at position {} (photoId {}, key {})",
                occupant.position(), occupant.photoId(), occupant.s3Key());

        media.delete(profileId, PhotoKeys.storageIdOf(occupant.s3Key()));
        catalog.deleteById(occupant.photoId());
    }
}
