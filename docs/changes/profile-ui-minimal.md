# Minimal profile UI refinement

## Compact scope record

- Intended outcome: make the desktop profile and sidebar feel quieter and more contemporary without changing navigation, profile data, or account actions.
- Out of scope: routes, authentication, profile APIs, subscription flows, photo behavior, mobile navigation structure, and backend work.
- Affected contract: none.
- Observable evidence:
  - Given the desktop Profile route, when it renders, then the active navigation state uses brand-colored icon/text without a surrounding green capsule or vertical bar.
  - Given the sidebar account utilities, when they render, then they sit on the sidebar canvas without a nested card or solid-green Edit action.
  - Given an active profile, when its details render, then activity uses a compact dot-and-label treatment and content groups do not rely on repeated divider lines.
  - Given a profile without a photo, when the placeholder renders, then it uses the neutral surface palette instead of a dominant green slab.
- Selected checks: Angular TypeScript compilation, production build, `git diff --check`, and visual comparison against the supplied desktop screenshot.
- Rollback: revert the scoped navbar, profile component, and profile placeholder style changes; no data migration or persisted-state cleanup is required.
- Promotion check: remains one reversible frontend-only visual slice with no unresolved product or contract decision.

## Evidence

- TypeScript application compilation passed.
- Angular production build passed.
- `git diff --check` passed for the scoped files.
- Source review confirmed the existing navigation, profile data, photo, subscription, and account action handlers are unchanged.
- Visual browser comparison is blocked because both available local browser sessions redirect `/profile` to the Keycloak sign-in screen.
