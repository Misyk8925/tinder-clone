package com.tinder.deckread.resource;

import com.tinder.deckread.dto.BuildingDeck;
import com.tinder.deckread.service.DeckQueryResult;
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
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/v2/deck")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DeckV2Resource {

    @Inject
    JsonWebToken jwt;

    @Inject
    DeckQueryService deckQueryService;

    @GET
    public Uni<Response> getDeck(
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        if (limit < 1 || limit > 100) {
            return Uni.createFrom().item(DeckResource.problem(
                    400, DeckQueryService.INVALID_LIMIT, "Invalid page limit",
                    "limit must be between 1 and 100."));
        }
        return deckQueryService.getDeckV2(jwt.getSubject(), cursor, limit)
                .map(this::response);
    }

    private Response response(DeckQueryResult result) {
        if (result instanceof DeckQueryResult.Page page) {
            return Response.ok(page.value()).build();
        }
        if (result instanceof DeckQueryResult.Building) {
            return Response.accepted(BuildingDeck.polling())
                    .header("Retry-After", "2")
                    .build();
        }
        DeckQueryResult.Failure failure = (DeckQueryResult.Failure) result;
        return DeckResource.problem(
                failure.status(), failure.code(), failure.title(), failure.detail());
    }
}
