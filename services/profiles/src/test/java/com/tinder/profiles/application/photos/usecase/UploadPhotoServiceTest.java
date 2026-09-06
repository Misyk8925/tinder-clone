package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.command.UploadPhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.model.StoredPhotoMedia;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.PhotoPolicy;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the upload orchestration that used to be entangled with the S3 client:
 * slot rules, media-service store and replacement of an occupied slot.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadPhotoService")
class UploadPhotoServiceTest {

    private static final String USER_ID = "user-1";
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final byte[] IMAGE = "image-bytes".getBytes();
    private static final String STORAGE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Mock private ProfilePhotoOwner owner;
    @Mock private PhotoCatalogPort catalog;
    @Mock private PhotoMediaPort media;
    @Mock private CleanupOrphanedPhotosService cleanupOrphaned;
    @Mock private DomainEventPublisherPort events;

    private UploadPhotoService service;

    @BeforeEach
    void setUp() {
        PhotoPolicy policy = new PhotoPolicy(
                5, 5L * 1024 * 1024, List.of("image/jpeg", "image/png"), 300, 4096);
        service = new UploadPhotoService(owner, catalog, media, cleanupOrphaned, policy, events);
    }

    @Test
    @DisplayName("Given a free slot, when a photo is uploaded, then it is stored and catalogued")
    void storesAndCataloguesOriginal() {
        givenProfileWithPhotos();
        givenStoredMedia();

        var uploaded = service.handle(command(0));

        ArgumentCaptor<PhotoDraft> draft = ArgumentCaptor.forClass(PhotoDraft.class);
        verify(catalog).save(draft.capture());
        then(draft.getValue().profileId()).isEqualTo(PROFILE_ID);
        then(draft.getValue().position()).isZero();
        then(draft.getValue().primary()).isTrue();
        then(draft.getValue().s3Key())
                .isEqualTo(PhotoKeys.variantKey(PROFILE_ID, STORAGE_ID, "original"));
        then(uploaded.photoId()).isEqualTo(STORAGE_ID);
        then(uploaded.smallUrl()).contains("/small.jpg");
        verify(events).publishCardChanged(PROFILE_ID);
        verify(media).store(PROFILE_ID, IMAGE, "image/png");
    }

    @Test
    @DisplayName("Given an occupied slot, when a photo is uploaded, then the previous objects and row are deleted")
    void replacesOccupiedSlot() {
        String previousStorageId = UUID.randomUUID().toString();
        StoredPhoto occupant = storedPhoto(previousStorageId, 0);
        givenProfileWithPhotos(occupant);
        givenStoredMedia();

        service.handle(command(0));

        verify(media).delete(PROFILE_ID, previousStorageId);
        verify(catalog).deleteById(occupant.photoId());
    }

    @Test
    @DisplayName("Given an empty album, when the first upload is not slot zero, then it is rejected")
    void rejectsFirstUploadIntoNonZeroSlot() {
        givenProfileWithPhotos();

        thenThrownBy(() -> service.handle(command(2)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Invalid position: 2");

        verify(media, never()).store(any(), any(), anyString());
    }

    @Test
    @DisplayName("Given an unreadable image, when the photos service rejects it, then storage is not catalogued")
    void rejectsUnreadableImage() {
        givenProfileWithPhotos();
        given(media.store(PROFILE_ID, IMAGE, "image/png"))
                .willThrow(new PhotoValidationException("Corrupted image"));

        thenThrownBy(() -> service.handle(command(0)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessage("Corrupted image");

        verify(catalog, never()).save(any());
    }

    @Test
    @DisplayName("Given an unsupported content type, when uploaded, then the photos service is not called")
    void rejectsUnsupportedContentType() {
        given(owner.profileIdOf(USER_ID)).willReturn(PROFILE_ID);

        thenThrownBy(() -> service.handle(new UploadPhotoCommand(
                USER_ID, IMAGE, "application/pdf", IMAGE.length, 0)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Invalid image type");

        verify(media, never()).store(any(), any(), anyString());
    }

    private UploadPhotoCommand command(int position) {
        return new UploadPhotoCommand(USER_ID, IMAGE, "image/png", IMAGE.length, position);
    }

    private void givenProfileWithPhotos(StoredPhoto... photos) {
        given(owner.profileIdOf(USER_ID)).willReturn(PROFILE_ID);
        given(catalog.findForProfile(PROFILE_ID)).willReturn(List.of(photos));
    }

    private void givenStoredMedia() {
        given(media.store(PROFILE_ID, IMAGE, "image/png")).willReturn(new StoredPhotoMedia(
                STORAGE_ID,
                PhotoKeys.variantKey(PROFILE_ID, STORAGE_ID, "original"),
                "https://cdn/photos/" + PROFILE_ID + "/" + STORAGE_ID + "/original.jpg",
                "https://cdn/photos/" + PROFILE_ID + "/" + STORAGE_ID + "/large.jpg",
                "https://cdn/photos/" + PROFILE_ID + "/" + STORAGE_ID + "/medium.jpg",
                "https://cdn/photos/" + PROFILE_ID + "/" + STORAGE_ID + "/small.jpg",
                "image/jpeg",
                1234L));
    }

    private StoredPhoto storedPhoto(String storageId, int position) {
        return new StoredPhoto(
                UUID.randomUUID(),
                PROFILE_ID,
                PhotoKeys.variantKey(PROFILE_ID, storageId, "original"),
                position == 0,
                position,
                "https://cdn/old.jpg",
                "image/jpeg",
                1234L,
                LocalDateTime.now());
    }
}
