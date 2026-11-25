package com.tinder.profiles.infrastructure.persistence.photos;

import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures.RecordingPhotoMedia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("SharedPhotoMapper")
class SharedPhotoMapperTest {

    private static final UUID ADA = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID GRACE = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    private final RecordingPhotoMedia photosService = new RecordingPhotoMedia();
    private final SharedPhotoMapper mapper = new SharedPhotoMapper(new PhotoDownloadUrlSigner(photosService));

    @Test
    @DisplayName("Given a catalogued photo stored as an S3 URL, when mapped for a client, then url is the photos service's signed URL")
    void mapsSignedDownloadUrlFromPhotosService() {
        // Given
        Photo photo = photo(ADA, STORAGE_ID, 0,
                "https://lunari-photos-pending.s3.eu-north-1.amazonaws.com/photos/%s/%s/original.jpg"
                        .formatted(ADA, STORAGE_ID));

        // When
        String url = mapper.toDtos(ADA, List.of(photo)).get(0).url();

        // Then
        then(url).isEqualTo(PhotoSignerFixtures.signedUrl(ADA, STORAGE_ID, "original"));
        then(url).doesNotContain("amazonaws.com");
    }

    @Test
    @DisplayName("Given a photo whose key is not a catalogued object path, when mapped, then the stored url is kept")
    void keepsUnparseableStoredUrl() {
        // Given
        Photo photo = new Photo();
        photo.setPhotoID(UUID.randomUUID());
        photo.setS3Key("ada/photo-0");
        photo.setUrl("https://cdn/ada/0");
        photo.setPosition(0);
        photo.setCreatedAt(LocalDateTime.now());

        // When
        String url = mapper.toDtos(ADA, List.of(photo)).get(0).url();

        // Then
        then(url).isEqualTo("https://cdn/ada/0");
        then(photosService.batches).isEmpty();
    }

    @Test
    @DisplayName("Given photos of several profiles, when grouped by profile, then all urls are signed in one photos-service call")
    void signsEveryProfilesPhotosInOneBatch() {
        // Given
        List<Photo> photos = List.of(
                owned(photo(ADA, "a-1", 1, null), ADA),
                owned(photo(ADA, "a-0", 0, null), ADA),
                owned(photo(GRACE, "g-0", 0, null), GRACE));

        // When
        Map<UUID, List<SharedPhotoDto>> byProfile = mapper.byProfileId(photos);

        // Then
        then(photosService.batches).hasSize(1);
        then(photosService.batches.get(0)).hasSize(3);
        then(byProfile.get(ADA)).extracting(SharedPhotoDto::url).containsExactly(
                PhotoSignerFixtures.signedUrl(ADA, "a-0", "original"),
                PhotoSignerFixtures.signedUrl(ADA, "a-1", "original"));
        then(byProfile.get(GRACE)).extracting(SharedPhotoDto::url).containsExactly(
                PhotoSignerFixtures.signedUrl(GRACE, "g-0", "original"));
    }

    private static Photo photo(UUID profileId, String storageId, int position, String url) {
        Photo photo = new Photo();
        photo.setPhotoID(UUID.randomUUID());
        photo.setS3Key("photos/%s/%s/original.jpg".formatted(profileId, storageId));
        photo.setUrl(url);
        photo.setPrimary(position == 0);
        photo.setPosition(position);
        photo.setContentType("image/jpeg");
        photo.setSize(2048L);
        photo.setCreatedAt(LocalDateTime.now());
        return photo;
    }

    private static Photo owned(Photo photo, UUID profileId) {
        photo.setProfile(ProfileJpaEntity.builder().profileId(profileId).build());
        return photo;
    }
}
