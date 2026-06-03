package com.tinder.deckread.resource;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.service.DeckQueryService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing deck read endpoint.
 *
 * <p>{@code GET /api/v1/deck?offset&limit} — the viewer is taken from the validated JWT
 * {@code sub} claim, never a query param, so a caller cannot request another user's deck.
 * Returns hydrated profiles in deck order, matching the legacy
 * {@code GET /api/v1/profiles/deck} response shape.
 */
@Path("/api/v1/deck")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DeckResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    DeckQueryService deckQueryService;

    @GET
    public Uni<List<DeckCardDto>> getDeck(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        UUID viewerId = UUID.fromString(jwt.getSubject());
        return deckQueryService.getDeck(viewerId, offset, limit);
    }
}
