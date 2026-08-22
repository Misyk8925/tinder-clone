# Bug: subscription row shows only €10/month

Severity: **major** · State: **fixed** · Scope: Profile Account list (`clients/tinder-client`)

## Expected versus actual

Given a non-premium profile with `isActive`, the Account list must show crown + “Upgrade to premium” + €10/month.

Actual: the middle row is blank on the left; only €10/month renders. Accessible name is only `€10/month`.

## Repro

1. Open Profile while signed in (or `npm run start:preview` → `/profile`).
2. Confirm Active badge is visible and user is not premium.
3. Account → middle row: price only.

## Root cause

The Active badge was switched to `lucide-icon name="circle"` without registering `Circle` in `LUCIDE_ICONS`. Lucide throws in `ngOnChanges`, which aborts the rest of that change-detection pass. The premium row’s label text is bound in update mode (badge text is create-mode static), so the label stays empty and `crown` never paints.

## Regression

`lucide-icons.spec.ts` — Given the profile Active badge uses circle, when icons are provided, then Circle is registered (and Crown for the premium row).

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| R.1 Selected mode | Done | bug-fix |
| B.1 Repro | Done | screenshot + preview DOM (`label` empty, crown no SVG) |
| B.2 Expected vs actual | Done | label+icon required; only badge showed |
| B.3 Confirmed | Done | a11y name `€10/month`; circle `hasSvg: false` |
| B.4 Root-cause | Done | unregistered `Circle` throws; aborts sibling CD |
| B.5 Contract | Done | none |
| B.6 Regression red | Done | Circle absent from provider list before fix |
| B.7 Smallest safe fix | Done | register Circle via `APP_LUCIDE_ICONS` |
| B.8 Targeted review | Done | also aria-label + numeric strokeWidth on premium row |
| P5.x Deploy | Done | `tinder-client` image rebuilt for local compose |
