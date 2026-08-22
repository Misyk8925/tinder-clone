# Deck Read rejects public Keycloak tokens

## Status

Fixed locally and deployed to the VPS on 2026-08-21. Deck Read started healthy with the public issuer override; one authenticated browser request remains the final user-path smoke check.

## Expected versus actual

Given a valid access token issued by the public Keycloak realm at `https://auth.misyk.tech/realms/spring`, `GET /api/v2/deck` must authenticate the caller and continue to Deck Read.

The production request returned `401 Unauthorized` because Deck Read discovered Keycloak through `http://keycloak:9080/realms/spring` and therefore expected the internal issuer value.

Severity: major. Scope: Deck Read v1/v2 HTTP authentication only; no HTTP response or data contract changes.

## Root cause

Keycloak advertises an issuer based on the request host. Internal discovery advertises `http://keycloak:9080/realms/spring`, while tokens acquired by the browser carry `https://auth.misyk.tech/realms/spring`. Deck Read configured only the internal discovery URL, so Quarkus rejected the otherwise valid public token on its `iss` claim.

## Fix and regression evidence

Deck Read keeps the internal `KEYCLOAK_REALM_URL` for metadata/JWK retrieval and now pins `KEYCLOAK_TOKEN_ISSUER` to the public realm. Issuer validation remains strict; it is not disabled with `issuer=any`.

`DeckReadCqrsBoundaryAcceptanceTest#publicTokenIssuerIsExplicitWhenOidcDiscoveryUsesTheInternalRealmUrl` protects both the Quarkus property and Compose wiring.

Rollback: restore the prior Deck Read configuration and image. That restores the known `401` for public tokens, so rollback is appropriate only if the new configuration prevents service startup.

## Workflow ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | `bug-fix`: established authenticated behaviour was violated in production |
| R.2 Project profile | Done | Repo-local bug record; English docs; native JUnit acceptance; Compose/VPS release |
| R.3 Omitted phases identified | Done | Every full-feature-only row is listed below |
| P1.1 Tracker/milestones | Mode-omit | Bug-fix mode uses this compact bug record |
| P1.2 Feature index | Mode-omit | Existing Deck Read feature index remains authoritative |
| P1.3 Decision tree | Mode-omit | Intended authentication behaviour is already established |
| P1.4 Frontier interview | Mode-omit | No unresolved product decision |
| P1.5 Category check | Mode-omit | Not a new product capability |
| P1.6 Wayfinder split | Mode-omit | One diagnosed configuration mechanism |
| P1.7 Shared-understanding gate | Mode-omit | Expected behaviour is established |
| P1.8 Concept | Mode-omit | No new concept or requirements |
| P1.9 Suggested solution alternatives | Mode-omit | Bug-fix selects the smallest safe correction |
| P1.10 Diagram | Mode-omit | No architecture change |
| P1.11 Concept self-check | Mode-omit | No concept phase |
| P1.12 Concept approval gate | Mode-omit | No owner decision required |
| P2.1 HTTP contract | Mode-omit | HTTP surface and responses are unchanged |
| P2.2 Event contract | Mode-omit | No event change |
| P2.3 WebSocket contract | Mode-omit | No WebSocket change |
| P2.4 Data contract | Mode-omit | No schema or data change |
| P2.5 Cross-cutting contract design | Mode-omit | Existing strict issuer policy is restored, not redesigned |
| P2.6 Contract error table | Mode-omit | Existing `401` contract remains correct for invalid tokens |
| P2.7 Contract uncertainty | Mode-omit | No contract uncertainty remains |
| P2.8 Continue to phase 3 | Mode-omit | Bug-fix mode does not run phase 2 |
| P3.1 Acceptance format selection | Mode-omit | Existing JUnit convention reused directly in B.6 |
| P3.2 FR acceptance suite | Mode-omit | No new FRs |
| P3.3 Contract-error acceptance suite | Mode-omit | No contract changes |
| P3.4 Traceability table | Mode-omit | Compact bug record links one repro to one regression check |
| P3.5 Combined contract check | Mode-omit | No phase-2/3 package |
| P3.6 Manual behaviour record | Mode-omit | Runtime smoke is tracked in P5.4 |
| P3.7 Combined approval gate | Mode-omit | No contract/behaviour decision required |
| B.1 Repro | Done | Production `GET /api/v2/deck?limit=20` returned `401` |
| B.2 Expected/actual/scope/severity | Done | Recorded above; major, Deck Read v1/v2 authentication |
| B.3 Confirmed | Done | Internal/public Keycloak discovery returned different issuers |
| B.4 Root cause | Done | Quarkus expected the internally discovered issuer for a public-host token |
| B.5 Contract inspected | Done | Gateway permits and forwards Deck bearer tokens; Deck Read remains authenticated; no boundary change |
| B.6 Regression red | Done | Focused JUnit check failed before the issuer configuration existed |
| B.7 Smallest safe fix | Done | Explicit expected issuer; internal metadata/JWK URL retained; no `issuer=any` |
| B.8 Regression green | Done | `DeckReadCqrsBoundaryAcceptanceTest`: 8 tests passed |
| P4.1 Slice plan | Done | One AFK config slice; biggest risk was weakening issuer validation |
| P4.2 Primary evidence | Done | Red/green architecture acceptance check |
| P4.3 Implementation | Done | Deck Read property plus Compose runtime override |
| P4.4 Test levels | Done | Contract/config acceptance and auth specialist check selected; unit/component/integration N/A for declarative config; authenticated system smoke blocked below |
| P4.5 Error path | Done | Mismatched internal/public issuer is the covered regression |
| P4.6 Review | Done | Explicit self-review: specification and engineering fit checked; no independent reviewer used |
| P4.7 Targeted defect review | Done | No additional confirmed defect in the changed surface |
| P4.8 Quality gates | Done | Focused test, package build and `git diff --check` passed |
| P4.9 Handoff | N/A | Same context continued |
| P4.10 Combined feature review | Mode-omit | One-slice bug-fix mode |
| P5.1 Build | Done | `mvn -B -DskipTests package` |
| P5.2 Security scan | N/A | No dependency or executable-code change; strict issuer remains enabled |
| P5.3 Deploy | Done | VPS Compose override deployed; Deck Read API recreated |
| P5.4 Smoke | Blocked | Health is `200`; authenticated browser smoke requires user-approved token transmission or a user retry |
| P5.5 Rollback | Done | VPS `docker-compose.yml.before-deck-read-issuer-fix` plus API recreate |
| P5.6 Monitoring | Blocked | Post-authenticated-request observation window has not started |
| P5.7 Docs | Done | This bug record contains diagnosis, evidence and rollback |
| P5.8 Open risks/blockers | Blocked | Production authenticated smoke remains outstanding |
