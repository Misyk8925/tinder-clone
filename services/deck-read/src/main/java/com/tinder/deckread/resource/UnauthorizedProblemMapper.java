package com.tinder.deckread.resource;

import com.tinder.deckread.dto.ProblemDetails;
import com.tinder.deckread.service.DeckQueryService;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** RFC 7807 representation for Quarkus security challenges on Deck endpoints. */
@Provider
public class UnauthorizedProblemMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {
        return Response.status(401)
                .type("application/problem+json")
                .entity(ProblemDetails.of(
                        401,
                        DeckQueryService.UNAUTHENTICATED,
                        "Authentication required",
                        "A valid bearer token is required."))
                .build();
    }
}
