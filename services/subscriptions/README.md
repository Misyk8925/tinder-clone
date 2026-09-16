# Subscriptions

Payment completion → premium entitlement. Stripe checkout/portal/webhook, then gRPC to Profiles.

Parent map: [Lunari README](../../README.md) · Domain: billing

## Boundary

**Owns.** `POST /api/v1/billing/checkout-session`, `portal-session`, `sync`, `POST /api/v1/webhook`. Mapping a paid Stripe event to a premium upgrade call.

**Does not own.** The Keycloak `USER_PREMIUM` role itself — [profiles](../profiles/README.md) assigns it. Paywall UI ([tinder-client](../../clients/tinder-client/README.md)). Rate limits ([gateway](../gateway/README.md)); the webhook route is intentionally unmetered by JWT.

**Neighbors.** mTLS gRPC `UpdatePremiumUser` to Profiles `:9010`. Gateway billing routes require a user JWT; webhook does not.

Not on the six-minute demo path. `./scripts/demo-up.sh` boots with placeholder Stripe so the process starts without calling Stripe.

## Contents

- Stripe session + webhook ingest.
- `GrpcMtlsConfig` / circuit breaker around Profiles.
- `POST /api/v1/billing/sync` — reconcile when localhost cannot receive webhooks.
- Port `:8095`.

## Decision

**Failure.** Billing secrets, webhook verification, and entitlement writes are a different failure mode from profile CRUD.

**Rejected.** Stripe inside Profiles. Trusting localhost to receive webhooks.

**Shipped.** Dedicated service. Local sync endpoint instead of a tunnel as the default demo path.

**Cost.** Premium in the demo only if the account already has `USER_PREMIUM`. Do not fill real Stripe keys to try Discover.

## Validate

```bash
mvn test
```
