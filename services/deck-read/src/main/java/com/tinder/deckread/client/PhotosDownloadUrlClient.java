package com.tinder.deckread.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/photos")
@RegisterRestClient(configKey = "photos")
public interface PhotosDownloadUrlClient {

    @POST
    @Path("/download-urls")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<DownloadUrlsResponse> downloadUrls(DownloadUrlsRequest request);

    record DownloadUrlItem(UUID ownerId, String storageId, String size, String namespace) {
    }

    record DownloadUrlsRequest(List<DownloadUrlItem> items) {
    }

    record DownloadUrlResult(UUID ownerId, String storageId, String size, String url) {
    }

    record DownloadUrlsResponse(List<DownloadUrlResult> items) {
    }
}
