# Minimal location entry

## Compact scope record

- Intended outcome: present the existing location decision as a focused, minimal first-run screen in light and dark modes.
- Out of scope: permission policy, geolocation persistence, route guards, service contracts, backend APIs, and navigation destinations.
- Affected contract: none. `GeoLocationService.requestPermission()`, skip persistence, denied retry, and `/discover` navigation remain unchanged.
- Observable acceptance evidence:
  - Given the location screen is idle, when it renders, then it shows one primary Allow action, one quiet Continue-without-location action, and a private-location explanation without the app navigation.
  - Given permission is denied, when the state renders, then retry instructions, Try again, and Continue-without-location remain available.
  - Given a narrow viewport, when the screen renders, then all copy and actions fit without horizontal overflow.
- Selected checks: `./node_modules/.bin/tsc -p tsconfig.app.json --noEmit --pretty false`; `npm run build -- --progress=false`; browser checks at 286 × 606 and 1024 × 768; source-versus-render comparison; semantic DOM review; `git diff --check`.
- Rollback: revert the location component and navbar route-hide condition; no persisted or server data needs migration.
- Promotion check: remains one reversible visual slice with no owner, privacy-policy, or contract decision.

## Evidence

- TypeScript application compilation passed.
- Angular production build passed; the location screen remains a lazy route.
- Browser rendering passed at the 286 × 606 reference size and at 1024 × 768. The onboarding navigation is absent and the desktop layout has no reserved sidebar gutter.
- Source and implementation were reviewed together in `clients/tinder-client/design-qa-location-entry-comparison.png`.
- The idle state exposes one primary button, one secondary button, and a labelled region in the semantic DOM.
- Permission and skip actions were not invoked during visual QA because they request device location or change the saved onboarding state. Their existing service calls and route destinations were preserved.
- `git diff --check` passed for the scoped files.
