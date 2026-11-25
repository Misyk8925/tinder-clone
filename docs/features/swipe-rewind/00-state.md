# State: swipe-rewind

Fallback repo-local tracker (Linear is not authorized in this environment).
Current phase: **5 — Release handoff ready (pre-deploy)** · 2026-11-25 · Branch: `feat/swipe-rewind` (`origin/main` @ `310f09b`)

## Phase milestones

| Phase | Artifact | Status | Approved by | Date |
|---|---|---|---|---|
| 1 Concept | [`01-concept.en.md`](01-concept.en.md), [`01-concept.ru.md`](01-concept.ru.md) | **Approved** | user («одобряю») | 2026-11-25 |
| 2+3 Contracts + behaviour | [`02-contracts/README.md`](02-contracts/README.md), [`03-behaviour/README.md`](03-behaviour/README.md) | **Approved** | user («да») | 2026-11-25 |
| 4 Implementation | [`04-implementation/plan.md`](04-implementation/plan.md), [`04-implementation/log.md`](04-implementation/log.md), [`04-implementation/qa-metrics.md`](04-implementation/qa-metrics.md) | **Done** | Fresh-context review fixes verified | 2026-11-25 |
| 5 Release | [`05-release/checklist.md`](05-release/checklist.md) | **Handed off (pre-deploy)** | — (deploy not requested) | 2026-11-25 |

## Gate: Phase 1 — Approve concept

- **Status:** `Approved` by user («одобряю», 2026-11-25), including explicit acceptance of `RISK-1`.

## Gate: Phases 2 + 3 — Approve contracts and executable behaviour

- **Artifacts:** [`02-contracts/README.md`](02-contracts/README.md), [`03-behaviour/README.md`](03-behaviour/README.md)
- **Status:** `Approved` by user («да», 2026-11-25).

## Decisions

| ID | Decision | Status | Source |
|---|---|---|---|
| D1 | Rewind отменяет только самый последний свайп (like или pass) в течение 5 минут; профиль возвращается первым в колоду | Approved | User request (2026-11-25) |
| D2 | Если свайп уже привёл к матчу — отмена запрещена (`409 Conflict`) | Approved | User request (2026-11-25) |
| D3 | Повторный запрос на отмену того же свайпа идемпотентен (ничего не меняет) | Approved | User request (2026-11-25) |
| D4 | Отмена оформляется компенсирующим событием `SwipeRevoked` (`tinder-contracts`), а не удалением свайпа; существующие контракты сохраняются | Approved | User request (2026-11-25) |
| D5 | Acceptance-тесты пишутся в стиле Given/When/Then на нативных фреймворках | Approved | User request + repo convention |
| Q1 | Rewind доступен всем авторизованным пользователям (с `RoleBasedRateLimitFilter` на Gateway, без `PremiumOrAdminFilter`) | Approved | User frontier round 1 (2026-11-25) |
| Q2 | `swipes-go` хранит append-only историю/последний свайп в `swipes_db` и локально потребляет `match.created` для проверки 409 без синхронного HTTP-вызова в `consumer` (плюс защитная проверка матча в `consumer` и `deck-read` при обработке `SwipeRevoked`) | Approved | User frontier round 1 (2026-11-25) |
| Q3 | В `consumer` сохраняется append-only история (без удаления свайпа), компенсируются существующие read-модели (`swipe_records`, `pending_likes`, Redis `swipes:exists:{swiperId}`) и добавляются метрики отмен; отдельная новая таблица пользовательских счётчиков не вводится | Approved | User frontier round 1 (2026-11-25) |
| Q4 | `POST /api/v1/swipes/rewind` (опциональное тело `{}` / `swipeEventId`), `200 OK` с `{ revokedSwipeEventId, revocationEventId, profile2Id, decision, revokedAt, alreadyRevoked }` (повторный вызов на тот же отменённый свайп идемпотентен, без дубликата события, глубина = 1), ошибки `400`, `401`, `403`, `404`, `409` (`REWIND_WINDOW_EXPIRED`/`SWIPE_ALREADY_MATCHED`/`NOT_LATEST_SWIPE`), `429`, `503`; возвращённый профиль можно свайпнуть повторно | Approved | User frontier round 1 + Phase-1 gate (2026-11-25) |
| Q5 | В `tinder-client` компактная кнопка Rewind (`rotate-ccw`) слева от Pass и на экране пустой колоды, `disabled` когда нет доступного свайпа / прошло > 5 мин / матч / запрос в полёте; на `200 OK` мгновенный возврат карточки первой (или перезагрузка колоды с `deck-read`), на `409` toast + disable | Approved | User frontier round 1 (2026-11-25) |

## Risk register (Open-risk view: 0 undecided risks)

| ID | Status | Likelihood | Impact | Risk | Mitigation / Owner |
|---|---|---|---|---|---|
| RISK-1 | **Accepted** (owner: user, 2026-11-25) | Medium | High | Гонка при локальном потреблении `match.created` в `swipes-go` (~1 с `match_event_outbox` `poll-interval-ms: 1000` + Kafka lag): пользователь может успеть получить `200 OK` на Rewind в течение ~1 с после взаимного лайка | Защитная проверка `isMutualMatch` в `consumer` при обработке `SwipeRevoked` (`IGNORED_MATCH`), `deck-read` сохраняет `dr:viewer:{id}:matched`, клиент `watchForMatch` отключает Rewind. |
| RISK-2 | **Mitigated** (2026-11-25) | Medium | Medium | Повторный свайп на возвращённый профиль: `SwipeRepository.upsertSwipe` использует `COALESCE`, а `ViewerMutationStore.applySwipe` использует `HSETNX` | Реализовано и покрыто тестами: `consumer` сбрасывает `decision1`/`decision2` в `NULL` (если нет матча) и игнорирует запоздавший `SwipeCreated` по `existsByRevokedSwipeEventId`; `deck-read` удаляет кандидата из `swipes`/`repeat-candidates`, проверяет `rewound` до `HSETNX`/`ZADD` в Lua и очищает `rewound` при свежем свайпе. |
| RISK-3 | **Mitigated** (2026-11-25) | Low | Medium | Нарушение порядка доставки Kafka между `swipe-created`/`swipe-saved` и `swipe-revoked`, а также между `deck` и `deck-read` | Реализовано и покрыто регрессионными тестами: ключ партиции = `profile1Id`; `consumer` использует `existsBySwiperIdAndSwipedIdAndEventIdNotAndCreatedAtAfter` + `existsByRevokedSwipeEventId`; `deck-read` сравнивает `swipeTs` vs `rewoundAt` в `APPLY_FIRST_SWIPE_SCRIPT` и `APPLY_SWIPE_REVOKED_SCRIPT` и хранит `dr:viewer:{id}:rewound`. |
| RISK-4 | **Mitigated** (2026-11-25) | Low | Medium | В `swipes-go` стоит `AllowAutoTopicCreation: false`, а у `consumer` нет one-shot сервиса миграций в `docker-compose.yml` | Реализовано: `docker-compose.yml` создаёт `swipe-revoked` (10p), `swipe-revoked.dlt` (10p), `match.created.dlt` (3p), запускает `V3_swipes_rewind.sql` и `consumer-migrations` (`V2_consumer_swipe_revocations.sql`), задаёт `DECK_READ_SWIPE_REVOKED_GROUP_ID`; миграции содержат идемпотентные блоки `GRANT`. |
| RISK-5 | **Deferred** (until deploy to `lunari.misyk.tech`; owner: user/deploy) | Low | Medium | Запись e2e-видео (`e2e-record`) для UI-изменений (`FR-10`) требует задеплоенного окружения (`lunari.misyk.tech`) с этим изменением | В Phase 4/5 шаг `e2e-record` честно помечен `Blocked` до деплоя; после деплоя выполнить `e2e-record --pr <n>`. |

## Open questions

- Нет открытых продуктовых, контрактных или технических вопросов.

## Phase ledger — `full-feature-delivery` / phase 4 (all slices S1–S6)

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P4.4 Integration / System E2E / NFR-1, NFR-2 | Blocked | Full Docker Compose stack (Kafka/Keycloak/Redis/Postgres) не запускается по умолчанию (`AGENTS.md`); `TestProfilesIntegration` skipped (нет `SWIPES_INTEGRATION_DATABASE_URL`); `e2e-record` требует деплоя на `lunari.misyk.tech` (`RISK-5`); `NFR-1`/`NFR-2` — `Not yet observed`. Unit, component, contract, migration-safety, accessibility и idempotency/reconciliation проверки — **Done** (все 7 модулей зелёные). |
| P4.1–P4.3 Slice plan, RED proof, implementation | Done | `04-implementation/plan.md` (S1–S6 с зависимостями и HITL/AFK); RED-база зафиксирована в P3.5; все 6 слайсов + миграции + Compose реализованы строго по контрактам |
| P4.5 Error-path evidence | Done | Все 9 HTTP-кодов (`400`, `401`, `403`, `404`, `409` ×3, `429`, `503`) + DLT/out-of-order/match-guard/duplicate пути покрыты тестами (`gateway`, `swipes-go`, `consumer`, `deck`, `deck-read`, `tinder-client`) |
| P4.6–P4.7 Fresh-context review & targeted defect review | Done | Независимый fresh-context ревьюер (`b6f98d69-8f51-4b54-b802-9b52618b76c1`) выявил 7 дефектов (2 blocker, 1 major, 4 minor); все 7 исправлены с регрессионными тестами (`04-implementation/log.md`) |
| P4.8 QA metrics / quality gates | Done | `04-implementation/qa-metrics.md` обновлён финальными цифрами; недоступные проверки честно помечены `Blocked` / `Not yet observed` |
| P4.9 Session handoff | N/A | Один поток исполнения; `plan.md` / `log.md` / `00-state.md` поддерживаются актуальными |
| P4.10 Combined-diff review | Done | Выполнен на общем диффе всех 6 слайсов (`b6f98d69-8f51-4b54-b802-9b52618b76c1`); все замечания закрыты и повторно прогнаны |

## Phase ledger — `full-feature-delivery` / phase 5 (pre-deploy handoff)

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P5.2 Security scan | Blocked | Локальный diff secret scan PASS + authz/rate-limit/no-PII проверены; CI SCA / container Trivy / SAST запускаются в CI перед деплоем |
| P5.3 Deploy | N/A | Деплой не запрашивался (`01-concept` Out of scope) |
| P5.4 Smoke / affected runtime checks | Blocked | Требует деплоя ветки на `lunari.misyk.tech` (`e2e-record --pr <n>`, `RISK-5`) |
| P5.6 Monitoring / observation window | Not yet observed | Окно наблюдения 24h после деплоя (`NFR-1`, `NFR-2`, метрики отмен) |
| P5.1 Build / validation | Done | Все 7 модулей (`tinder-contracts` 14/14, `gateway` 14/14, `swipes-go` PASS, `consumer` 19/19, `deck` 16/16, `deck-read` 11/11, `tinder-client` 112/112 + `npm run build`) + `validate-swipe-rewind-contracts.py` PASS |
| P5.5 Rollback note | Done | Зафиксирован в `05-release/checklist.md` (expand-only миграции → безопасный code-only откат) |
| P5.7 Docs / runbook | Done | OpenAPI/AsyncAPI/Data контракты + runbook в `05-release/checklist.md` |
| P5.8 Open risk / blocker | Done | 0 нерешённых рисков (`RISK-1` Accepted, `RISK-2..RISK-4` Mitigated, `RISK-5` Deferred), 0 открытых blocker-дефектов |
