package com.tinder.deckread.client;

import com.tinder.deckread.dto.DeckCardDto;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replaces cached public/CDN photo URLs with fresh presigned GETs at query time.
 * Redis keeps the durable locator so a five-minute signature cannot go stale in
 * the projection.
 */
@ApplicationScoped
public class DeckPhotoUrlRewriter {

    private static final Logger LOG = Logger.getLogger(DeckPhotoUrlRewriter.class);

    private final PhotosDownloadUrlClient photos;
    private final boolean enabled;

    @Inject
    public DeckPhotoUrlRewriter(
            @RestClient PhotosDownloadUrlClient photos,
            @ConfigProperty(name = "deck-read.photos.presign-enabled", defaultValue = "true")
            boolean enabled
    ) {
        this.photos = photos;
        this.enabled = enabled;
    }

    public Uni<List<DeckCardDto>> rewrite(List<DeckCardDto> cards) {
        if (!enabled || photos == null || cards == null || cards.isEmpty()) {
            return Uni.createFrom().item(cards == null ? List.of() : cards);
        }
        List<PhotosDownloadUrlClient.DownloadUrlItem> items = new ArrayList<>();
        for (DeckCardDto card : cards) {
            if (card.photos() == null) {
                continue;
            }
            for (DeckCardDto.Photo photo : card.photos()) {
                PhotoObjectRef.parse(card.profileId(), photo.url()).ifPresent(ref ->
                        items.add(new PhotosDownloadUrlClient.DownloadUrlItem(
                                ref.ownerId(), ref.storageId(), ref.variant(), PhotoObjectRef.NAMESPACE)));
            }
        }
        if (items.isEmpty()) {
            return Uni.createFrom().item(cards);
        }
        return photos.downloadUrls(new PhotosDownloadUrlClient.DownloadUrlsRequest(items))
                .onFailure().recoverWithItem(error -> {
                    logPresignFailure(error);
                    return new PhotosDownloadUrlClient.DownloadUrlsResponse(List.of());
                })
                .map(response -> apply(cards, response));
    }

    /**
     * Falling back to the stored locators keeps the deck serving through a transient photos
     * outage, but those URLs are unsigned and will not load. A 401/403 means the internal
     * secret is missing or wrong — a misconfiguration that never self-heals and would
     * otherwise sit behind a warning while every deck card renders broken.
     */
    private static void logPresignFailure(Throwable error) {
        if (isAuthFailure(error)) {
            LOG.error("Photo presign rejected: check PHOTOS_INTERNAL_AUTH_SECRET matches the photos "
                    + "service. Deck cards are being served with unsigned URLs and will not load.", error);
            return;
        }
        LOG.warn("Photo presign failed; serving stored deck URLs", error);
    }

    private static boolean isAuthFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebApplicationException webApplicationException) {
                int status = webApplicationException.getResponse().getStatus();
                if (status == 401 || status == 403) {
                    return true;
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static List<DeckCardDto> apply(
            List<DeckCardDto> cards,
            PhotosDownloadUrlClient.DownloadUrlsResponse response
    ) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return cards;
        }
        Map<String, String> byObject = new LinkedHashMap<>();
        for (PhotosDownloadUrlClient.DownloadUrlResult item : response.items()) {
            if (item == null || item.url() == null || item.url().isBlank()) {
                continue;
            }
            byObject.put(key(item.ownerId(), item.storageId(), item.size()), item.url());
        }
        if (byObject.isEmpty()) {
            return cards;
        }
        List<DeckCardDto> rewritten = new ArrayList<>(cards.size());
        for (DeckCardDto card : cards) {
            if (card.photos() == null || card.photos().isEmpty()) {
                rewritten.add(card);
                continue;
            }
            List<DeckCardDto.Photo> photos = card.photos().stream()
                    .map(photo -> replace(card.profileId(), photo, byObject))
                    .toList();
            rewritten.add(new DeckCardDto(
                    card.profileId(), card.name(), card.age(), card.city(), card.bio(), card.isActive(),
                    card.preferences(), photos, card.hobbies()));
        }
        return List.copyOf(rewritten);
    }

    private static DeckCardDto.Photo replace(
            UUID profileId,
            DeckCardDto.Photo photo,
            Map<String, String> byObject
    ) {
        return PhotoObjectRef.parse(profileId, photo.url())
                .map(ref -> {
                    String signed = byObject.get(key(ref.ownerId(), ref.storageId(), ref.variant()));
                    return signed == null ? photo : new DeckCardDto.Photo(photo.photoId(), signed, photo.order());
                })
                .orElse(photo);
    }

    private static String key(UUID ownerId, String storageId, String size) {
        return ownerId + "|" + storageId + "|" + size;
    }
}
