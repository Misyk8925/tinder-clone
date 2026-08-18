/**
 * Use cases, one package per feature ({@code profile}, {@code photos}).
 *
 * <p>Every feature package is laid out the same way:
 * <ul>
 *   <li>{@code command/} — a record per write intent;</li>
 *   <li>{@code query/} — transport-neutral read models;</li>
 *   <li>{@code model/} — data shared by commands and views;</li>
 *   <li>{@code port/in/} — read boundaries implemented by a query adapter;</li>
 *   <li>{@code port/out/} — everything the use cases need from the outside;</li>
 *   <li>{@code usecase/} — one {@code @Service} per use case, entry point {@code handle(...)};</li>
 *   <li>{@code support/} — {@code @Component} helpers and configuration-bound policy records;</li>
 *   <li>{@code exception/} — a feature exception hierarchy carrying stable error codes.</li>
 * </ul>
 *
 * <p>This layer depends on {@code domain..} and the JDK. Spring appears only as
 * {@code @Service}/{@code @Component}/{@code @Transactional} — no property
 * annotations (policies are injected as values), no web, persistence or
 * serialization types.
 */
package com.tinder.profiles.application;
