package com.tinder.deckread.resource;

import com.tinder.deckread.dto.ProblemDetails;
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

/** Deprecated bare-array adapter backed only by the local read model. */
@Path("/api/v1/deck")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DeckResource {

    static final int MAX_LIMIT = 100;

    @Inject
    JsonWebToken jwt;

    @Inject
    DeckQueryService deckQueryService;

    @GET
    public Uni<Response> getDeck(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_LIMIT) {
            return Uni.createFrom().item(problem(
                    400, DeckQueryService.INVALID_PAGINATION, "Invalid pagination",
                    "offset must be non-negative and limit must be between 1 and 100."));
        }
        return deckQueryService.isReadModelReady()
                .flatMap(ready -> ready
                        ? deckQueryService.getDeckV1(jwt.getSubject(), offset, limit)
                                .map(cards -> Response.ok(cards).build())
                        : Uni.createFrom().item(problem(
                                503, DeckQueryService.READ_MODEL_NOT_READY,
                                "Deck read model is recovering",
                                "Profile backfill and event catch-up have not completed.")))
                .onFailure().recoverWithItem(problem(
                        503, DeckQueryService.READ_MODEL_NOT_READY,
                        "Deck read model is recovering",
                        "The local read model is not available."));
    }

    static Response problem(int status, String code, String title, String detail) {
        return Response.status(status)
                .type("application/problem+json")
                .entity(ProblemDetails.of(status, code, title, detail))
                .build();
    }
}
