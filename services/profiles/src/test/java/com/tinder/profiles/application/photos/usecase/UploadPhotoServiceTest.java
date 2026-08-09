package com.tinder.profiles.application.photos.usecase;

import com.tinder.profiles.application.photos.command.UploadPhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.ImageDimensions;
import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.PhotoVariants;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.port.out.ImageVariantsPort;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.application.photos.port.out.PhotoStoragePort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.application.photos.support.PhotoPolicy;
import com.tinder.profiles.application.photos.support.ProfilePhotoOwner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the upload orchestration that used to be entangled with the S3 client:
 * slot rules, variant fan-out and replacement of an occupied slot.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadPhotoService")
class UploadPhotoServiceTest {

    private static final String USER_ID = "user-1";
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final byte[] IMAGE = "image-bytes".getBytes();

    @Mock private ProfilePhotoOwner owner;
    @Mock private PhotoCatalogPort catalog;
    @Mock private PhotoStoragePort storage;
    @Mock private ImageVariantsPort images;
    @Mock private CleanupOrphanedPhotosService cleanupOrphaned;

    private UploadPhotoService service;

    @BeforeEach
    void setUp() {
        PhotoPolicy policy = new PhotoPolicy(
                5, 5L * 1024 * 1024, List.of("image/jpeg", "image/png"), 300, 4096);
        service = new UploadPhotoService(owner, catalog, storage, images, cleanupOrphaned, policy);
    }

    @Test
    @DisplayName("stores four variants and catalogues the original")
    void storesVariantsAndCataloguesOriginal() {
        givenProfileWithPhotos();
        givenRenderableImage();
        given(storage.publicUrl(anyString())).willAnswer(call -> "https://cdn/" + call.getArgument(0));

        var uploaded = service.handle(command(0));

        verify(storage, times(4)).put(anyString(), any(), eq("image/jpeg"));
        ArgumentCaptor<PhotoDraft> draft = ArgumentCaptor.forClass(PhotoDraft.class);
        verify(catalog).save(draft.capture());
        then(draft.getValue().profileId()).isEqualTo(PROFILE_ID);
        then(draft.getValue().position()).isZero();
        then(draft.getValue().primary()).isTrue();
        then(draft.getValue().s3Key())
                .isEqualTo(PhotoKeys.variantKey(PROFILE_ID, uploaded.photoId(), "original"));
        then(uploaded.smallUrl()).contains("/small.jpg");
    }

    @Test
    @DisplayName("replacing an occupied slot deletes the previous objects and row")
    void replacesOccupiedSlot() {
        String previousStorageId = UUID.randomUUID().toString();
        StoredPhoto occupant = storedPhoto(previousStorageId, 0);
        givenProfileWithPhotos(occupant);
        givenRenderableImage();
        given(storage.publicUrl(anyString())).willReturn("https://cdn/photo.jpg");

        service.handle(command(0));

        PhotoKeys.allVariantKeys(PROFILE_ID, previousStorageId)
                .forEach(key -> verify(storage).delete(key));
        verify(catalog).deleteById(occupant.photoId());
    }

    @Test
    @DisplayName("rejects a first upload into a slot other than zero")
    void rejectsFirstUploadIntoNonZeroSlot() {
        givenProfileWithPhotos();

        thenThrownBy(() -> service.handle(command(2)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Invalid position: 2");

        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("rejects an unreadable image before touching storage")
    void rejectsUnreadableImage() {
        givenProfileWithPhotos();
        given(images.probe(IMAGE)).willReturn(Optional.empty());

        thenThrownBy(() -> service.handle(command(0)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessage("Corrupted image");

        verify(storage, never()).put(anyString(), any(), anyString());
        verify(catalog, never()).save(any());
    }

    @Test
    @DisplayName("rejects an image smaller than the policy minimum")
    void rejectsTooSmallImage() {
        givenProfileWithPhotos();
        given(images.probe(IMAGE)).willReturn(Optional.of(new ImageDimensions(100, 100)));

        thenThrownBy(() -> service.handle(command(0)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessage("Image too small");

        verify(images, never()).render(any());
    }

    @Test
    @DisplayName("rejects an unsupported content type")
    void rejectsUnsupportedContentType() {
        given(owner.profileIdOf(USER_ID)).willReturn(PROFILE_ID);

        thenThrownBy(() -> service.handle(new UploadPhotoCommand(
                USER_ID, IMAGE, "application/pdf", IMAGE.length, 0)))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Invalid image type");
    }

    private UploadPhotoCommand command(int position) {
        return new UploadPhotoCommand(USER_ID, IMAGE, "image/png", IMAGE.length, position);
    }

    private void givenProfileWithPhotos(StoredPhoto... photos) {
        given(owner.profileIdOf(USER_ID)).willReturn(PROFILE_ID);
        given(catalog.findForProfile(PROFILE_ID)).willReturn(List.of(photos));
    }

    private void givenRenderableImage() {
        given(images.probe(IMAGE)).willReturn(Optional.of(new ImageDimensions(1024, 768)));
        given(images.render(IMAGE)).willReturn(new PhotoVariants(
                "original".getBytes(), "large".getBytes(), "medium".getBytes(), "small".getBytes()));
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
