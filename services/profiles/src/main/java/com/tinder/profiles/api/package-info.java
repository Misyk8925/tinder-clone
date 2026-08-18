/**
 * Inbound adapters — everything that drives the application layer from outside:
 * REST controllers ({@code profile}, {@code photos}), the gRPC service
 * ({@code grpc}) and the scheduled triggers ({@code scheduling}).
 *
 * <p>Adapters here depend only on {@code application..}: commands, query views
 * and use-case services. They never reach into {@code infrastructure..} or the
 * {@code domain..} model — enforced by
 * {@code com.tinder.profiles.architecture.CleanArchitectureTest}.
 */
package com.tinder.profiles.api;
