package com.tinder.deckread.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

/**
 * Calls the deck (write) service to ensure a fresh deck exists for a viewer.
 *
 * <p>Mapped to {@code POST {deck-ensure base url}/api/v1/internal/deck/ensure?viewerId=...}.
 * This endpoint is currently plain HTTP (not behind mTLS). Returns {@code true} if the deck
 * is fresh or was rebuilt; {@code false} on failure. Configure the base URL and a short
 * timeout via {@code quarkus.rest-client.deck-ensure.*}.
 */
@Path("/api/v1/internal/deck")
@RegisterRestClient(configKey = "deck-ensure")
public interface DeckEnsureClient {

    @POST
    @Path("/ensure")
    Uni<Boolean> ensure(@QueryParam("viewerId") UUID viewerId);
}
