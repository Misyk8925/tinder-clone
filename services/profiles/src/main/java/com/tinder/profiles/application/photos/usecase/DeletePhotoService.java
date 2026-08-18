package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.command.DeletePhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoAccessDeniedException;
import com.tinder.profiles.application.photos.exception.PhotoNotFoundException;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoStoragePort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Removes a photo's stored variants and its catalogue entry. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeletePhotoService {

    private final ProfilePhotoOwner owner;
    private final PhotoCatalogPort catalog;
    private final PhotoStoragePort storage;
    private final DomainEventPublisherPort events;

    @Transactional
    public void handle(DeletePhotoCommand cmd) {
        UUID profileId = owner.profileIdOf(cmd.userId());
        StoredPhoto photo = catalog.findById(cmd.photoId())
                .orElseThrow(() -> new PhotoNotFoundException(cmd.photoId()));

        if (!profileId.equals(photo.profileId())) {
            throw new PhotoAccessDeniedException(cmd.photoId());
        }

        String storageId = PhotoKeys.storageIdOf(photo.s3Key());
        PhotoKeys.allVariantKeys(profileId, storageId).forEach(storage::delete);
        catalog.deleteById(photo.photoId());
        events.publishCardChanged(profileId);

        log.info("Deleted photo {} (storage id {}) of profile {}", photo.photoId(), storageId, profileId);
    }
}
