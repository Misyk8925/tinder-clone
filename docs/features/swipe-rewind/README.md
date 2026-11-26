# Swipe Rewind — отмена последнего свайпа

Repo-local tracker: [`00-state.md`](00-state.md) (Linear не авторизован в этой среде).

Индекс канонических артефактов фичи. Спецификации, миграции и тесты не копируются сюда — на них ведутся ссылки.

| Артефакт | Назначение |
|---|---|
| [`00-state.md`](00-state.md) | Состояние трекера, решения, реестр рисков, фазовый леджер (Phases 1–5) и гейты |
| [`01-concept.en.md`](01-concept.en.md) / [`01-concept.ru.md`](01-concept.ru.md) | (Phase 1 — Approved) Короткий концепт (EN + RU): резюме, FR-1..FR-10, NFR-1..NFR-6, Out of scope |
| [`02-contracts/README.md`](02-contracts/README.md) | (Phase 2 — Approved) Ссылки на канонические OpenAPI/AsyncAPI, `tinder-contracts`, SQL-миграции, UI/state-контракт |
| [`03-behaviour/README.md`](03-behaviour/README.md) | (Phase 3 — Approved & Green) Трассируемость FR-1..FR-10 и всех HTTP-ошибок к исполняемым Given/When/Then тестам |
| [`04-implementation/plan.md`](04-implementation/plan.md), [`log.md`](04-implementation/log.md), [`qa-metrics.md`](04-implementation/qa-metrics.md) | (Phase 4 — Done) План по слайсам S1–S6, лог итераций и ревью-фиксов, QA-метрики |
| [`05-release/checklist.md`](05-release/checklist.md) | (Phase 5 — Pre-deploy handoff) Чеклист сборки, безопасности, отката, мониторинга и runbook |

## Затронутые канонические компоненты

- `services/tinder-contracts` — новое событие `SwipeRevokedEventV1` (`v1`), существующие DTO/события не меняются.
- `services/gateway` — маршрут `swipes-rewind` (`POST /api/v1/swipes/rewind`) с `RoleBasedRateLimitFilter` (без `PremiumOrAdminFilter`).
- `services/swipes-go` — `POST /api/v1/swipes/rewind`, append-only `swipe_history` / `swipe_revocations`, локальная проекция `match.created` (`swipe_matches`), проверка 5-минутного окна / матча / идемпотентности / `403 PROFILE_OWNERSHIP_DENIED`, публикация `SwipeRevoked`.
- `services/consumer` — потребление `swipe-revoked`, append-only `swipe_revocations`, компенсирующее обновление `swipe_records` / `pending_likes` / Redis `swipes:exists:*`, защитные проверки `IGNORED_MATCH` / `IGNORED_STALE` / `existsByRevokedSwipeEventId`, метрики `consumer_swipes_revoked_total`.
- `services/deck` — потребление `swipe-revoked` и возврат профиля на первую позицию в `deck:{viewerId}` (Redis ZSET).
- `services/deck-read` — потребление `swipe-revoked`, очистка `swipes`/`repeat-candidates`, приоритетный ключ `dr:viewer:{viewerId}:rewound`, защита от out-of-order `swipe-saved` в Lua, санитизированный DLT-сериализатор.
- `clients/tinder-client` — кнопка Rewind (`rotate-ccw`, `aria-label="Rewind"`) в `DiscoverComponent` (на панели действий и на пустой колоде) + `SwipeService.rewind()`.
- `migrations/migration/`, `docker/postgres/`, `docker-compose.yml` — `V3_swipes_rewind.sql`, `V2_consumer_swipe_revocations.sql` (с идемпотентными `GRANT`), one-shot `consumer-migrations`, топики `swipe-revoked`, `swipe-revoked.dlt`, `match.created.dlt`.

## Phase ledger

Актуальные таблицы леджера ведутся в [`00-state.md`](00-state.md).
