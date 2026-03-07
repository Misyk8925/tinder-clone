package com.tinder.profiles.photos.media;

import com.tinder.profiles.config.MediaServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final MediaServiceProperties mediaServiceProperties;

    public MediaUploadIntentResponse createUploadIntent(MediaUploadIntentRequest request) {
        try {
            return internalClient()
                    .post()
                    .uri("/internal/media/upload-intents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MediaUploadIntentResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("create upload intent", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Media service is unavailable while creating upload intent", ex);
        }
    }

    public MediaAssetResponse completeUpload(UUID mediaId, String uploadKey) {
        try {
            return internalClient()
                    .post()
                    .uri("/internal/media/{mediaId}/complete", mediaId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CompleteUploadRequest(uploadKey))
                    .retrieve()
                    .bodyToMono(MediaAssetResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("complete upload", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Media service is unavailable while completing upload", ex);
        }
    }

    public MediaAssetResponse getMedia(UUID mediaId) {
        try {
            return internalClient()
                    .get()
                    .uri("/internal/media/{mediaId}", mediaId)
                    .retrieve()
                    .bodyToMono(MediaAssetResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("get media", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Media service is unavailable while fetching media status", ex);
        }
    }

    public String getDownloadUrl(UUID mediaId, String variant) {
        try {
            DownloadUrlResponse response = internalClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/media/{mediaId}/download-url")
                            .queryParam("variant", variant)
                            .build(mediaId))
                    .retrieve()
                    .bodyToMono(DownloadUrlResponse.class)
                    .block();

            if (response == null || response.url() == null || response.url().isBlank()) {
                throw new IllegalStateException("Media service returned an empty download URL");
            }
            return response.url();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("get media download url", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Media service is unavailable while generating download URL", ex);
        }
    }

    public void deleteMedia(UUID mediaId) {
        try {
            internalClient()
                    .delete()
                    .uri("/internal/media/{mediaId}", mediaId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("delete media", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Media service is unavailable while deleting media", ex);
        }
    }

    public void uploadToPresignedUrl(String uploadUrl, String contentType, byte[] data) {
        try {
            WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                    .build()
                    .put()
                    .uri(uploadUrl)
                    .contentType(MediaType.parseMediaType(contentType))
                    .bodyValue(data)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.releaseBody();
                        }

                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> reactor.core.publisher.Mono.error(
                                        buildPresignedUploadException(response.statusCode().value(), body)));
                    })
                    .block();
        } catch (WebClientResponseException ex) {
            throw mapRemoteException("upload binary to presigned url", ex);
        } catch (WebClientRequestException ex) {
            throw new IllegalStateException("Failed to upload file to S3 using presigned URL", ex);
        }
    }

    private WebClient internalClient() {
        return webClientBuilder
                .baseUrl(mediaServiceProperties.getBaseUrl())
                .build();
    }

    private RuntimeException mapRemoteException(String operation, WebClientResponseException ex) {
        String body = Objects.toString(ex.getResponseBodyAsString(), "").trim();
        String message = String.format(
                Locale.ROOT,
                "Media service failed to %s: HTTP %d%s",
                operation,
                ex.getRawStatusCode(),
                body.isEmpty() ? "" : " - " + body
        );

        if (ex.getRawStatusCode() >= 400 && ex.getRawStatusCode() < 500) {
            if (ex.getRawStatusCode() == 400 || ex.getRawStatusCode() == 404 || ex.getRawStatusCode() == 422) {
                return new IllegalArgumentException(message, ex);
            }
            return new IllegalStateException(message, ex);
        }
        return new IllegalStateException(message, ex);
    }

    private IllegalStateException buildPresignedUploadException(int statusCode, String body) {
        if (statusCode == 301 && body != null && body.contains("PermanentRedirect")) {
            String endpoint = extractXmlValue(body, "Endpoint");
            return new IllegalStateException(
                    "Presigned upload failed due to S3 bucket region/endpoint mismatch. "
                            + "Set AWS_S3_BUCKET to the real bucket and AWS_REGION (or AWS_DEFAULT_REGION) "
                            + "to the bucket region."
                            + (endpoint == null ? "" : " Expected endpoint: " + endpoint)
            );
        }

        return new IllegalStateException(
                "Presigned upload failed: HTTP "
                        + statusCode
                        + (body == null || body.isBlank() ? "" : " - " + body)
        );
    }

    private String extractXmlValue(String xmlBody, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        int start = xmlBody.indexOf(startTag);
        int end = xmlBody.indexOf(endTag);

        if (start < 0 || end < 0 || end <= start + startTag.length()) {
            return null;
        }
        return xmlBody.substring(start + startTag.length(), end).trim();
    }

    private record CompleteUploadRequest(String uploadKey) {
    }

    private record DownloadUrlResponse(
            UUID mediaId,
            String variant,
            String url
    ) {
    }
}
