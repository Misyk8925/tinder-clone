package com.tinder.deckread.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhotoObjectRef")
class PhotoObjectRefTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    @Test
    @DisplayName("parses CloudFront, bucket and already-presigned URLs")
    void parsesKnownLayouts() {
        assertThat(PhotoObjectRef.parse(PROFILE_ID,
                "https://cdn.example.test/photos/%s/%s/medium.jpg".formatted(PROFILE_ID, STORAGE_ID)))
                .hasValue(new PhotoObjectRef.Ref(PROFILE_ID, STORAGE_ID, "medium"));
        assertThat(PhotoObjectRef.parse(PROFILE_ID,
                "https://bucket.s3.eu-north-1.amazonaws.com/photos/%s/%s/original.jpg?X-Amz-Signature=abc"
                        .formatted(PROFILE_ID, STORAGE_ID)))
                .hasValue(new PhotoObjectRef.Ref(PROFILE_ID, STORAGE_ID, "original"));
        assertThat(PhotoObjectRef.parse(PROFILE_ID,
                "photos/%s/%s/small.jpg".formatted(PROFILE_ID, STORAGE_ID)))
                .hasValue(new PhotoObjectRef.Ref(PROFILE_ID, STORAGE_ID, "small"));
    }

    @Test
    @DisplayName("ignores locators that are not catalogued photo objects")
    void ignoresUnknownLayouts() {
        assertThat(PhotoObjectRef.parse(PROFILE_ID, "https://cdn.example.test/card.jpg")).isEmpty();
        assertThat(PhotoObjectRef.parse(PROFILE_ID, "")).isEmpty();
        assertThat(PhotoObjectRef.parse(null, "photos/x/y/original.jpg")).isEmpty();
    }
}
