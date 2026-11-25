package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * Pins the profiles → photos batch signing call to the contract in
 * {@code services/photos/app/api.py} ({@code POST /api/v1/photos/download-urls}).
 */
@DisplayName("PhotosServiceAdapter download URLs")
class PhotosServiceAdapterDownloadUrlsTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    @DisplayName("Given two variants to sign, when signed, then one POST to /download-urls carries both and the signed urls map back to their refs")
    void signsManyVariantsInOneBatchCall() {
        // Given
        PhotoVariantRef original = new PhotoVariantRef(OWNER, "s-1", "original");
        PhotoVariantRef small = new PhotoVariantRef(OWNER, "s-2", "small");
        PhotosServiceAdapter adapter = adapterRespondingWith(HttpStatus.OK, """
                {"items":[
                  {"ownerId":"%1$s","storageId":"s-1","size":"original","url":"/media/photos/%1$s/s-1/original.jpg?exp=1&sig=a"},
                  {"ownerId":"%1$s","storageId":"s-2","size":"small","url":"/media/photos/%1$s/s-2/small.jpg?exp=1&sig=b"}
                ]}""".formatted(OWNER));

        // When
        Map<PhotoVariantRef, String> urls = adapter.presignedDownloadUrls(List.of(original, small));

        // Then
        then(calls).hasValue(1);
        ClientRequest request = lastRequest.get();
        then(request.method()).isEqualTo(HttpMethod.POST);
        then(request.url().getPath()).isEqualTo("/api/v1/photos/download-urls");
        then(bodyOf(request))
                .contains("\"ownerId\":\"" + OWNER + "\"")
                .contains("\"storageId\":\"s-1\"", "\"size\":\"original\"")
                .contains("\"storageId\":\"s-2\"", "\"size\":\"small\"")
                .contains("\"namespace\":\"photos\"");
        then(urls).containsOnly(
                Map.entry(original, "/media/photos/%s/s-1/original.jpg?exp=1&sig=a".formatted(OWNER)),
                Map.entry(small, "/media/photos/%s/s-2/small.jpg?exp=1&sig=b".formatted(OWNER)));
    }

    @Test
    @DisplayName("Given nothing to sign, when signed, then the photos service is not called")
    void skipsCallForEmptyBatch() {
        // Given
        PhotosServiceAdapter adapter = adapterRespondingWith(HttpStatus.OK, "{\"items\":[]}");

        // When
        Map<PhotoVariantRef, String> urls = adapter.presignedDownloadUrls(List.of());

        // Then
        then(urls).isEmpty();
        then(calls).hasValue(0);
    }

    @Test
    @DisplayName("Given the photos service fails, when signed, then a PhotoStorageException is raised for the caller to fall back on")
    void mapsServerErrorToStorageException() {
        // Given
        PhotosServiceAdapter adapter = adapterRespondingWith(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"code\":\"PHOTO_STORAGE_ERROR\",\"message\":\"disk unavailable\"}");

        // When / Then
        thenThrownBy(() -> adapter.presignedDownloadUrls(List.of(new PhotoVariantRef(OWNER, "s-1", "original"))))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessageContaining("disk unavailable");
    }

    private PhotosServiceAdapter adapterRespondingWith(HttpStatus status, String json) {
        ExchangeFunction exchange = request -> {
            calls.incrementAndGet();
            lastRequest.set(request);
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json)
                    .build());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://photos:8070")
                .exchangeFunction(exchange)
                .build();
        return new PhotosServiceAdapter(webClient);
    }

    private static String bodyOf(ClientRequest request) {
        MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
        List<HttpMessageWriter<?>> writers = ExchangeStrategies.withDefaults().messageWriters();
        request.body().insert(captured, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return writers;
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return captured.getBodyAsString().block();
    }
}
