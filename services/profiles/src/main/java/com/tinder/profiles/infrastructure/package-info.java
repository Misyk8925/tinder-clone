/**
 * Outbound adapters: persistence, caching, messaging and external clients.
 *
 * <p>Conventions in this layer:
 * <ul>
 *   <li>a class implementing an application port is named {@code *Adapter} and
 *       prefixed with its technology ({@code Jpa}, {@code Redis}, {@code S3},
 *       {@code Keycloak}, …);</li>
 *   <li>everything else is a technology-internal collaborator — {@code *Repository},
 *       {@code *Client}, {@code *Mapper}, {@code *Service} — reachable only from
 *       inside this layer;</li>
 *   <li>beans are declared with {@code @Component}: {@code @Service} is reserved
 *       for application use cases;</li>
 *   <li>adapters translate at the boundary and hold no business rules.</li>
 * </ul>
 */
package com.tinder.profiles.infrastructure;
