package com.tinder.deckread.client;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The photos service rejects media requests without the shared secret. If deck-read stops
 * sending it, presign 401s and every deck card is served with an unsigned URL that will not
 * load — so the header is pinned here rather than left to configuration review.
 */
@DisplayName("PhotosInternalAuthHeadersFactory")
class PhotosInternalAuthHeadersFactoryTest {

    @Test
    @DisplayName("Given a configured secret, when photos is called, then X-Internal-Auth is sent")
    void sendsTheInternalSecret() {
        PhotosInternalAuthHeadersFactory factory = new PhotosInternalAuthHeadersFactory();
        factory.internalAuthSecret = java.util.Optional.of("s3cret");

        var headers = factory.update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());

        assertThat(headers.getFirst(PhotosInternalAuthHeadersFactory.INTERNAL_AUTH_HEADER))
                .isEqualTo("s3cret");
    }

    @Test
    @DisplayName("Given the secret is absent entirely, then the service still starts and sends no header")
    void toleratesAnAbsentSecret() {
        PhotosInternalAuthHeadersFactory factory = new PhotosInternalAuthHeadersFactory();
        factory.internalAuthSecret = java.util.Optional.empty();

        var headers = factory.update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());

        assertThat(headers.containsKey(PhotosInternalAuthHeadersFactory.INTERNAL_AUTH_HEADER)).isFalse();
    }

    @Test
    @DisplayName("Given no configured secret, when photos is called, then no empty header is sent")
    void omitsTheHeaderWhenUnconfigured() {
        PhotosInternalAuthHeadersFactory factory = new PhotosInternalAuthHeadersFactory();
        factory.internalAuthSecret = java.util.Optional.of("  ");

        var headers = factory.update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());

        assertThat(headers.containsKey(PhotosInternalAuthHeadersFactory.INTERNAL_AUTH_HEADER)).isFalse();
    }
}
