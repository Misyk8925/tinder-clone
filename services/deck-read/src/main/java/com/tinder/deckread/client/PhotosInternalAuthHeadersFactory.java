package com.tinder.deckread.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import java.util.Optional;

/**
 * Attaches the shared internal secret to photos-service calls.
 * <p>
 * The photos service serves no media request without {@code X-Internal-Auth}. Without this the
 * presign call returns 401, {@link DeckPhotoUrlRewriter} falls back to the stored locators, and
 * deck cards render with unsigned URLs that the browser cannot load.
 */
@ApplicationScoped
public class PhotosInternalAuthHeadersFactory implements ClientHeadersFactory {

    static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    /**
     * Optional, and deliberately so: SmallRye converts an empty value to null, so a plain String
     * would fail the whole deployment whenever PHOTOS_INTERNAL_AUTH_SECRET is unset — turning a
     * missing secret into a service that will not start at all.
     */
    @ConfigProperty(name = "deck-read.photos.internal-auth-secret")
    Optional<String> internalAuthSecret;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> outgoingHeaders
    ) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        if (internalAuthSecret != null) {
            internalAuthSecret
                    .filter(secret -> !secret.isBlank())
                    .ifPresent(secret -> headers.add(INTERNAL_AUTH_HEADER, secret));
        }
        return headers;
    }
}
