package com.tinder.profiles.config.http;

import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.NameResolverProvider.NameResolverSpec;

import java.time.Duration;

/**
 * Reactor Netty {@link HttpClient} for calls to other compose services (photos, location, moderation, keycloak).
 *
 * <p>Reactor Netty resolves host names with Netty's own DNS resolver and caches each answer for the TTL the DNS
 * server returns, with no upper bound by default. Docker's embedded DNS answers container names with a TTL of
 * 600 seconds, so after a redeploy recreates a container on a new IP, the default client keeps dialing the old IP
 * for up to ten minutes ({@code NoRouteToHostException}). Capping the cache lets the client follow the new IP
 * within {@link #DNS_CACHE_MAX_TTL}.
 */
public final class InternalServiceHttpClient {

    static final Duration DNS_CACHE_MAX_TTL = Duration.ofSeconds(5);

    private InternalServiceHttpClient() {
    }

    public static HttpClient create() {
        return HttpClient.create().resolver(InternalServiceHttpClient::boundDnsCache);
    }

    static void boundDnsCache(NameResolverSpec spec) {
        spec.cacheMaxTimeToLive(DNS_CACHE_MAX_TTL);
    }
}
