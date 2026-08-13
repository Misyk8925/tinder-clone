package com.tinder.deckread.resource;

import com.tinder.deckread.dto.ProblemDetails;
import com.tinder.deckread.service.DeckQueryService;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.MediaType;

import java.lang.annotation.Annotation;

/** Makes pre-resource authentication failures follow the RFC 7807 contract. */
@Provider
@Priority(Priorities.ENTITY_CODER)
public class ProblemResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (response.getStatus() == 401 && request.getUriInfo().getPath().matches("api/v[12]/deck")) {
            response.setEntity(
                    ProblemDetails.of(
                            401,
                            DeckQueryService.UNAUTHENTICATED,
                            "Authentication required",
                            "A valid bearer token is required."),
                    new Annotation[0],
                    MediaType.valueOf("application/problem+json"));
            response.getHeaders().putSingle("Content-Type", "application/problem+json");
        }
    }
}
