# Subscriptions crash-loops and consumer stays unhealthy on local Compose

## Status

Fixed 2026-08-22.

Severity: major · Scope: `subscriptions` startup, `consumer` Compose healthcheck

## Expected versus actual

Given `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d`, subscriptions and consumer must become healthy.

Actual: subscriptions crash-looped (`Could not resolve placeholder 'STRIPE_RETURN_URL'`). Consumer stayed `unhealthy` because `GET /actuator/health` returned 404.

## Repro

`.env` has no `STRIPE_RETURN_URL`. Compose required it only via `:?` / local overlay, and an already-created subscriptions container never received the new env key. Consumer `pom.xml` had no Actuator, while Compose probed `/actuator/health`.

## Root cause

1. Host interpolation had no default, so a missing `.env` key left Spring prod `${STRIPE_RETURN_URL}` unresolved after a stale container recreate.
2. Consumer is a web service without `spring-boot-starter-actuator`, so the existing wget healthcheck cannot succeed.

## Fix and regression evidence

Compose and `application-prod.yaml` default `STRIPE_RETURN_URL` to `http://localhost:4200/profile`. Local overlay sets checkout success/cancel/return to that client route. Consumer ships Actuator and wget.

`DeckReadCqrsBoundaryAcceptanceTest.runtimeComposeKeepsSubscriptionsReturnUrlAndConsumerHealthReachable` — red before the default/actuator strings existed, green after.
