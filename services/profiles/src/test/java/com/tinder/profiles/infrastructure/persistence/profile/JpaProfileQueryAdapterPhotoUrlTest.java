package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.application.profile.query.ProfileView;
import com.tinder.profiles.infrastructure.cache.ProfileIdentityCacheService;
import com.tinder.profiles.infrastructure.cache.ResilientCacheManager;
import com.tinder.profiles.infrastructure.persistence.photos.Photo;
import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures.RecordingPhotoMedia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaProfileQueryAdapter photo urls")
class JpaProfileQueryAdapterPhotoUrlTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private ResilientCacheManager resilientCacheManager;
    @Mock
    private ProfileIdentityCacheService profileIdentityCacheService;

    private final RecordingPhotoMedia photosService = new RecordingPhotoMedia();

    @Test
    @DisplayName("Given a catalogued photo stored as an S3 URL, when the owner reads their profile, then url comes from the photos service, not S3")
    void getMyProfileSignsPhotoUrlThroughPhotosService() {
        // Given
        Photo photo = new Photo();
        photo.setPhotoID(UUID.randomUUID());
        photo.setS3Key("photos/%s/%s/original.jpg".formatted(PROFILE_ID, STORAGE_ID));
        photo.setUrl("https://lunari-photos-pending.s3.eu-north-1.amazonaws.com/photos/%s/%s/original.jpg"
                .formatted(PROFILE_ID, STORAGE_ID));
        photo.setPrimary(true);
        photo.setPosition(0);
        photo.setContentType("image/jpeg");
        photo.setSize(2048L);
        photo.setCreatedAt(LocalDateTime.now());

        ProfileJpaEntity profile = ProfileJpaEntity.builder()
                .profileId(PROFILE_ID)
                .userId("user-1")
                .name("Ada")
                .age(29)
                .gender("FEMALE")
                .city("Kyiv")
                .isActive(true)
                .photos(List.of(photo))
                .hobbies(List.of())
                .build();
        given(profileRepository.findByUserId("user-1")).willReturn(profile);

        JpaProfileQueryAdapter adapter = new JpaProfileQueryAdapter(
                profileRepository,
                resilientCacheManager,
                profileIdentityCacheService,
                new PhotoDownloadUrlSigner(photosService));

        // When
        ProfileView view = adapter.getMyProfile("user-1");

        // Then
        then(view.photos()).hasSize(1);
        then(view.photos().get(0).url())
                .isEqualTo(PhotoSignerFixtures.signedUrl(PROFILE_ID, STORAGE_ID, "original"))
                .doesNotContain("amazonaws.com");
        then(photosService.batches).hasSize(1);
    }
}
