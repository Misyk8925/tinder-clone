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
