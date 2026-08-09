/**
 * Spring wiring. Nothing here is imported by {@code domain..} or
 * {@code application..}.
 *
 * <p>Conventions in this layer:
 * <ul>
 *   <li>{@code props/} holds every {@code @ConfigurationProperties} class, each an
 *       immutable record using constructor binding with {@code @DefaultValue};
 *       {@code @Value} is not used anywhere in the service;</li>
 *   <li>{@code application/} binds those properties to the application layer's
 *       policy value objects;</li>
 *   <li>the remaining packages contribute infrastructure beans (AWS, Redis,
 *       Keycloak, security, mTLS, resilience, observability).</li>
 * </ul>
 */
package com.tinder.profiles.config;
