package com.tinder.deckread.client;

import com.tinder.deckread.dto.DeckCardDto;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeckPhotoUrlRewriter")
class DeckPhotoUrlRewriterTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PHOTO_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    @Test
    @DisplayName("Given a cached public photo URL, when a deck page is rewritten, then url is a fresh presign")
    void rewritesCachedPublicUrl() {
        String stored = "https://cdn.example.test/photos/%s/%s/original.jpg"
                .formatted(PROFILE_ID, STORAGE_ID);
        String signed = stored + "?X-Amz-Signature=abc";
        PhotosDownloadUrlClient photos = request -> {
            assertThat(request.items()).hasSize(1);
            assertThat(request.items().get(0).ownerId()).isEqualTo(PROFILE_ID);
            assertThat(request.items().get(0).storageId()).isEqualTo(STORAGE_ID);
            assertThat(request.items().get(0).size()).isEqualTo("original");
            return Uni.createFrom().item(new PhotosDownloadUrlClient.DownloadUrlsResponse(List.of(
                    new PhotosDownloadUrlClient.DownloadUrlResult(
                            PROFILE_ID, STORAGE_ID, "original", signed))));
        };
        DeckPhotoUrlRewriter rewriter = new DeckPhotoUrlRewriter(photos, true);
        DeckCardDto card = card(stored);

        List<DeckCardDto> rewritten = rewriter.rewrite(List.of(card)).await().indefinitely();

        assertThat(rewritten.get(0).photos().get(0).url()).isEqualTo(signed);
        assertThat(rewritten.get(0).photos().get(0).photoId()).isEqualTo(PHOTO_ID);
    }

    @Test
    @DisplayName("Given photos signing is disabled, when rewritten, then stored urls are kept")
    void disabledKeepsStoredUrls() {
        String stored = "https://cdn.example.test/photos/%s/%s/original.jpg"
                .formatted(PROFILE_ID, STORAGE_ID);
        DeckPhotoUrlRewriter rewriter = new DeckPhotoUrlRewriter(request -> {
            throw new AssertionError("photos client must not be called");
        }, false);

        List<DeckCardDto> rewritten = rewriter.rewrite(List.of(card(stored))).await().indefinitely();

        assertThat(rewritten.get(0).photos().get(0).url()).isEqualTo(stored);
    }

    @Test
    @DisplayName("Given the photos service fails, when rewritten, then stored urls are kept")
    void photosFailureKeepsStoredUrls() {
        String stored = "https://cdn.example.test/photos/%s/%s/original.jpg"
                .formatted(PROFILE_ID, STORAGE_ID);
        DeckPhotoUrlRewriter rewriter = new DeckPhotoUrlRewriter(
                request -> Uni.createFrom().failure(new RuntimeException("photos down")),
                true);

        List<DeckCardDto> rewritten = rewriter.rewrite(List.of(card(stored))).await().indefinitely();

        assertThat(rewritten.get(0).photos().get(0).url()).isEqualTo(stored);
    }

    private static DeckCardDto card(String url) {
        return new DeckCardDto(
                PROFILE_ID, "Ada", 29, "Kyiv", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50),
                List.of(new DeckCardDto.Photo(PHOTO_ID, url, 0)),
                List.of());
    }
}
