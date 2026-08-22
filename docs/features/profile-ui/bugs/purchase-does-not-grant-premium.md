# Bug: purchase succeeds but Profile stays non-premium

Severity: **major** · State: **fixed** · Scope: subscriptions entitlement, Profile UI

## Expected versus actual

Given a completed Stripe Checkout, Profile must show Premium Active (`USER_PREMIUM`).

Actual: checkout created `cus_V7RqiPwLKs1xe6` for the signed-in user; `stripe_event_inbox` stayed empty; `profiles.is_premium` stayed false. UI also checked Keycloak role `premium`, which is never granted.

## Repro

1. Local stack, subscribe from Profile.
2. Complete Stripe Checkout and return to `/profile`.
3. Account still shows Upgrade to premium.

## Root cause

1. Stripe Dashboard cannot POST to localhost. No webhook → no gRPC activate → no `USER_PREMIUM`.
2. Profile used `hasRole('premium')` instead of `USER_PREMIUM`, so even a granted role would not show.

## Regression

`StripeWebhookProcessServiceTest.givenActiveStripeSubscriptionWhenReconciledThenActivatesPremium` — Given a paid Stripe customer and no webhook, when reconciled, then premium is activated from Stripe.

`keycloak.service.spec.ts` — Profile checks `USER_PREMIUM` / `hasPremium()`, not `premium`.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| B.1 Repro | Done | checkout log + empty inbox + is_premium=f |
| B.4 Root-cause | Done | no local webhook + wrong role name |
| B.7 Fix | Done | `POST /api/v1/billing/sync` + Profile sync/refresh |
| P5 Deploy | Done | rebuild subscriptions + tinder-client |
