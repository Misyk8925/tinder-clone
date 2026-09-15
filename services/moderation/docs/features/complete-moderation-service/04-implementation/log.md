# Implementation log

## 2026-09-06 — slice 1: synchronous context-aware moderation

Primary acceptance started red because no REST/security/provider wiring existed.
Implemented typed `LlmAnalysisRequest`, OpenAI moderation and Gemini adjudication adapters,
Spring wiring, simple BCrypt/Basic security, REST DTO/controller, and stable errors.

Self-review findings and changes:

- Context aggregate validation ran only when LLM adjudication happened. Moved the 20-message/16-KiB check into preprocessing and proved classifier is not called.
- Missing provider keys became `IllegalArgumentException` and could map to client `400`. Changed them to `ProviderException`/retryable provider failure.
- Invalid JSON and access denied lacked the documented JSON errors. Added explicit handlers.
- Oversized text was rejected by bean validation as `400`. Routed it through preprocessing as content-too-large.
- Contract content-type names differed from the existing domain enum. Corrected OpenAPI/AsyncAPI to `MESSAGE`, `PROFILE_DESCRIPTION`, `PHOTO`, `REPORT` before further implementation.

Result: slice acceptance GREEN; full feature remains RED because slices 2–5 are not implemented.

## 2026-09-15 — slices 4–5: async delivery and final evidence

Implemented transactional result/review/policy outbox writes, a retrying publisher,
versioned Kafka command consumption, five-attempt DLQ recovery, dependency readiness,
provider-failure `HOLD`, lockout, retention cleanup, secure cookies, login UI, and
keyless/performance/security evidence.

Failed iterations and plan corrections:

- Clean build failed after adding transactions to final Kotlin service classes. CGLIB
  instantiated a proxy whose final methods read uninitialized fields. Moved outbox calls
  into the already transactional JDBC adapters, which also makes state + outbox atomic.
- Browser smoke failed login with `403`: the custom form omitted CSRF. Added the real
  request token to the form; repeat browser smoke passed all four admin pages at 400 px.
- Fresh-context review confirmed eight defects: PostgreSQL duplicate insert aborted the
  transaction; retention omitted image URLs; readiness was always green; DLQ diagnostics
  could leak parser input; v1 accepted another schema version; REST result events conflicted
  with AsyncAPI nullability; expired lockout immediately relocked; cookie security defaulted
  off. Each was corrected and covered by a focused check.

Final local result without provider keys: 87 regression/component checks passed,
23 acceptance checks passed, 5 Docker-dependent Testcontainers checks skipped/blocked,
contract validation passed, bootJar built, keyless curl smoke passed, and browser smoke passed.
