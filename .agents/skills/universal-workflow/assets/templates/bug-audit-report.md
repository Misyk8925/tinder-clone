# Bug audit — pass #<n> — <date>

**Destination:** a comment/attachment on the audit's Linear milestone. (No Linear: save as `docs/bug-audits/<date>-pass-<n>/report.md` — see `references/bug-hunt.md`.)

Previous pass: #<n-1> (<date>) | none — first pass for this codebase

## Summary

Found <N> new issues (<breakdown by severity>), <M> recurring (flagged for review), <K> closed since the previous pass.

This reflects what this pass found, not a completeness guarantee — a bug hunt samples the codebase; it doesn't prove the absence of bugs it didn't find.

## New

| ID | Title | Severity | Scope |
|----|-------|----------|-------|

## Recurring — needs manual review, not auto-resolution

| ID | Title | Previously closed in pass # | Why it's back (fix was wrong? incomplete? this is a false positive? unclear) |
|----|-------|------------------------------|-------------------------------------------------------------------------------|

## Closed since last pass

| ID | Title | Verified how (old repro no longer reproduces? fix linked and merged?) |
|----|-------|--------------------------------------------------------------------------|

## Next audit

Cadence per `docs/contracts/conventions.md`: <e.g. "before next production release" | "every 10 completed features">
