package com.tinder.profiles.infrastructure.persistence.photos;

import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("SharedPhotoMapper")
class SharedPhotoMapperTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    private S3Presigner presigner;

    @AfterEach
    void closePresigner() {
        if (presigner != null) {
            presigner.close();
        }
    }

    @Test
    @DisplayName("Given a catalogued photo stored as a public S3 URL, when mapped for a client, then url is a presigned GET")
    void mapsPresignedDownloadUrl() {
        presigner = S3Presigner.builder()
                .region(Region.EU_NORTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret")))
                .build();
        PhotoDownloadUrlSigner signer = new PhotoDownloadUrlSigner(presigner, PhotoSignerFixtures.testPhotos());
        SharedPhotoMapper mapper = new SharedPhotoMapper(signer);

        Photo photo = new Photo();
        photo.setPhotoID(UUID.randomUUID());
        photo.setS3Key("photos/%s/%s/original.jpg".formatted(PROFILE_ID, STORAGE_ID));
        photo.setUrl("https://test-bucket.s3.eu-north-1.amazonaws.com/photos/%s/%s/original.jpg"
                .formatted(PROFILE_ID, STORAGE_ID));
        photo.setPrimary(true);
        photo.setPosition(0);
        photo.setContentType("image/jpeg");
        photo.setSize(2048L);
        photo.setCreatedAt(LocalDateTime.now());

        String url = mapper.toDtos(PROFILE_ID, List.of(photo)).get(0).url();

        then(url).contains("X-Amz-Signature");
        then(url).contains("X-Amz-Expires");
        then(url).isNotEqualTo(photo.getUrl());
        then(url).contains(STORAGE_ID);
    }

    @Test
    @DisplayName("Given a photo whose key is not a catalogued object path, when mapped, then the stored url is kept")
    void keepsUnparseableStoredUrl() {
        presigner = S3Presigner.builder()
                .region(Region.EU_NORTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret")))
                .build();
        SharedPhotoMapper mapper = new SharedPhotoMapper(new PhotoDownloadUrlSigner(presigner, PhotoSignerFixtures.testPhotos()));

        Photo photo = new Photo();
        photo.setPhotoID(UUID.randomUUID());
        photo.setS3Key("ada/photo-0");
        photo.setUrl("https://cdn/ada/0");
        photo.setPosition(0);
        photo.setCreatedAt(LocalDateTime.now());

        then(mapper.toDtos(PROFILE_ID, List.of(photo)).get(0).url()).isEqualTo("https://cdn/ada/0");
    }
}
