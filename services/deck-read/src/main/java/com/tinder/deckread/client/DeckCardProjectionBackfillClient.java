package com.tinder.deckread.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

/**
 * Starts or resumes the Profiles Deck Card projection backfill over mTLS.
 *
 * <p>Mapped to {@code POST {profiles-backfill url}/api/v1/profiles/internal/deck-card-projection/backfills/{runId}}.
 */
@Path("/api/v1/profiles/internal/deck-card-projection/backfills")
@RegisterRestClient(configKey = "profiles-backfill")
public interface DeckCardProjectionBackfillClient {

    @POST
    @Path("/{runId}")
    Uni<Void> startOrResume(@PathParam("runId") UUID runId);
}
