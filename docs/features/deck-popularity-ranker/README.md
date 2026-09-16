# Deck popularity ranker

Ranks the deck by a signal the platform already collects and throws away: how often each
profile is liked.

Built on the experiment platform from
[deck-ranking-experiments](../deck-ranking-experiments/README.md) — it ships as one more
named variant, assignable by the same registry, measurable by the same outcome screen.

| Path | What it holds |
|---|---|
| [`00-state.md`](00-state.md) | Approval log and decisions |
| [`01-concept.ru.md`](01-concept.ru.md) | Concept — **awaiting the phase-1 gate** |

## Why this and not embeddings

The offline screen in `services/deck/load-tests/deck-sim` measured both. A factorisation of
the swipe matrix only overtakes a `log(1+likes)` counter at around eight likes received per
profile, and loses below it. Today's density is below that, so the counter ships first and
the factorisation waits for a trigger FR-7 makes observable.

## Phase ledger

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P1.1 Tracker container | Blocked | Linear not authorized in this session; `00-state.md` is the fallback |
| P1.12 Gate | Blocked | Concept complete; stopped for explicit owner approval |
| P1.2 Feature index | Done | This file |
| P1.3 Decision tree | Done | Shown in session; 4 `Approved`, 2 `Fact`, 0 `Open` |
| P1.4 Frontier interview | Done | No unresolved frontier — every consequential choice is an owner answer from 2026-09-10 or a repository fact, cited in `00-state.md` |
| P1.5 Category check | Done | user/trigger/boundaries/stakeholders inferred; numbers from the screen; integration and failure in sections 6 and 7 |
| P1.6 Wayfinder split | N/A | One coherent decision area, already resolved by the screen |
| P1.7 Shared understanding | Done | The screen results and the ordering were confirmed by the owner before drafting |
| P1.8–P1.9 Concept | Done | 7 FRs, 6 numbered NFRs, 6 rejected alternatives |
| P1.10 Diagram | N/A | One service, one counter, one background job — prose covers it |
| P1.11 Self-check | Done | Redis persistence, `SwipeEventConsumer` payload, `@EnableScheduling`, and the mTLS channel to Consumer all verified in the repository |
