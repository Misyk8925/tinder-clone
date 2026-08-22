# Bug: Stripe redirect URLs point at the subscriptions service

Severity: **major** · State: **fixed** · Scope: local Compose + subscriptions Stripe config

## Expected versus actual

Given local Compose, Stripe checkout success/cancel and billing-portal return must send the browser to the Angular client (`http://localhost:4200/profile`).

Actual: live `subscriptions` had `STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL` at `http://subscriptions:8095/...`. `.env.local` used `http://localhost:8095/success|cancel`. Return was `http://localhost:4200/subscriptions/return`, which is not a client route.

## Repro

1. `docker compose -f docker-compose.yml -f docker-compose.local.yml exec subscriptions printenv STRIPE_SUCCESS_URL STRIPE_CANCEL_URL STRIPE_RETURN_URL`
2. Success/cancel are backend hosts; return path is not a client route.

## Root cause

Compose requires host `STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL`. Local env pointed those at the subscriptions port. The local overlay only overrode return, and to a non-existent `/subscriptions/return` path. Spring defaults in `application.yaml` also used `:8095`.

## Regression

`DeckReadCqrsBoundaryAcceptanceTest.runtimeComposeKeepsSubscriptionsReturnUrlAndConsumerHealthReachable` — local overlay and Spring defaults must use `http://localhost:4200/profile`, not `:8095` or `/subscriptions/return`.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | bug-fix |
| B.1 Repro | Done | live printenv showed backend success/cancel |
| B.2 Expected vs actual | Done | browser must return to `/profile` |
| B.3 Confirmed | Done | `.env.local` `:8095`; overlay missed success/cancel |
| B.4 Root-cause | Done | host env + overlay only set return |
| B.5 Contract | Done | Stripe redirect URLs are browser URLs, not service URLs |
| B.6 Regression red | Done | architecture test now forbids `:8095` redirects |
| B.7 Smallest safe fix | Done | overlay + yaml defaults + `.env.local` |
| B.8 Targeted review | Done | recreate subscriptions to pick up env |
| P5.x Deploy | Done | `subscriptions` recreated locally |
