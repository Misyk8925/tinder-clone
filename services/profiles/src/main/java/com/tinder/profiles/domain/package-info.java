/**
 * The domain model: the profile aggregate and its value objects.
 *
 * <p>Conventions in this layer:
 * <ul>
 *   <li>plain Java only — no Spring, no JPA, no Lombok, no Jackson;</li>
 *   <li>state changes go through behaviour methods, never setters;</li>
 *   <li>value objects are records that validate themselves in their constructor;</li>
 *   <li>rule violations raise {@code DomainValidationException}, which the
 *       application layer translates before it can reach an adapter.</li>
 * </ul>
 */
package com.tinder.profiles.domain;
