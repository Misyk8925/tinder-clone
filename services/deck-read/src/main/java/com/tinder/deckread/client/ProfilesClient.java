package com.tinder.deckread.client;

import com.tinder.contracts.dto.SharedProfileDto;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hydrates profile IDs into full profiles via the profiles service internal endpoint.
 *
 * <p>Mapped to {@code GET {profiles base url}/by-ids?ids=<comma-separated-uuids>}.
 * The configured base URL already includes {@code /api/v1/profiles/internal}.
 *
 * <p>This endpoint REQUIRES mTLS ({@code client-auth: need} on the profiles internal
 * connector). Configure the keystore/truststore via {@code quarkus.rest-client.profiles.*}.
 */
@Path("/")
@RegisterRestClient(configKey = "profiles")
@Produces(MediaType.APPLICATION_JSON)
public interface ProfilesClient {

    /**
     * Hydrate the given comma-separated profile IDs.
     *
     * <p>Wrapped in fault tolerance so a slow or failing profiles service degrades gracefully
     * instead of tying up connections: a per-call {@link Timeout}, and a {@link CircuitBreaker}
     * that trips open after sustained failures and fast-fails subsequent calls (the caller then
     * serves stale cached profiles). SmallRye Fault Tolerance natively supports the {@code Uni}
     * return type, so this stays fully non-blocking.
     */
    @GET
    @Path("/by-ids")
    @Timeout(value = 2000, unit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 5000, successThreshold = 3)
    Uni<List<SharedProfileDto>> getByIds(@QueryParam("ids") String commaSeparatedIds);
}
