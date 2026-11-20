# How to learn this repository

You do not need to explain fifteen services. You need **one user path** and **three boundaries**. This file is the tutorial: what to read, in what order, and what to leave shut in an interview.

Spoken six minutes: [script.md](script.md). Why those three boundaries exist: [decisions.md](decisions.md). Recruiter-facing stories: [GitHub issues labeled `story`](https://github.com/Misyk8925/tinder-clone/issues?q=is%3Aissue+label%3Astory).

---

## Русский

### Зачем этот файл

Репозиторий большой, потому что у Discover, записи свайпа, фото и биллинга разные отказы. На собеседовании это плюс только если ты защищаешь **matching-путь**, а не перечисляешь папки. Остальное достаточно уметь назвать одной фразой: «живёт отдельно, потому что падает иначе».

### Порядок чтения (примерно вечер, не неделя)

1. Корень [README.md](../../README.md) — первый экран и таблица «Six-minute path». Не учи Key Endpoints наизусть.
2. Этот файл.
3. [script.md](script.md) — что говорить, пока открыт UI.
4. [decisions.md](decisions.md) — отклонённые альтернативы (dual-write, live join на GET, JWT на каждом порту).
5. Двенадцать файлов ниже — открой каждый и проговори вслух, что он делает.
6. [talk-track.md](talk-track.md) — короткие ответы, если прервут.

### Один продуктовый путь

Два заранее созданных аккаунта → Discover (`GET /api/v2/deck`) → взаимный лайк → матч → текстовый чат.

Браузер ходит **только** в gateway. Карточки читает Deck Read. Порядок колоды строит `services/deck`. Свайп пишет `services/swipes-go`. Матч появляется из outbox, не из «отправили в Kafka в том же HTTP-потоке».

### Двенадцать файлов, которые стоит уметь открыть

| # | Файл | Зачем |
|---|---|---|
| 1 | `clients/tinder-client/src/app/core/services/profile.service.ts` | Клиент зовёт `/api/v2/deck`. |
| 2 | `services/gateway/src/main/resources/application.yml` | Маршруты на статическом `*_SERVICE_URL`, не через service discovery. |
| 3 | `services/gateway/src/main/java/com/tinder/gateway/RoleBasedRateLimitFilter.java` | Лимит ключуется как `routeId + user + role`. Capacity `0` — это deny. |
| 4 | `docs/architecture/README.md` + `matching-path.puml` | Картинка matching-пути. ArchUnit её не гоняет (разные JAR). |
| 4b | `docs/architecture/README.md` таблица **Inside a service** + `profiles-layers.puml` / `profiles-features.puml` / `match-modules.puml` / `deck-http.puml` | Модули *внутри* сервиса. ArchUnit только где пакеты — DAG; циклы названы и не проверяются. |
| 4c | `services/deck-read/src/test/java/com/tinder/deckread/architecture/DeckReadCqrsBoundaryAcceptanceTest.java` | Read-side не имеет права владеть алгоритмом Deck и его Redis-ключами. |
| 5 | `docs/features/deck-read-cqrs/README.md` | Граница write/read для Discover. |
| 6 | `services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxService.java` | Событие профиля пишется в той же транзакции, что и строка. |
| 7 | `services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxBatchProcessor.java` | Батч: claim → publish → retry → dead-letter, без тихого drop. |
| 8 | `services/consumer/src/main/java/com/tinder/clone/consumer/outbox/SwipeOutboxEventDispatcher.java` | Тот же приём для свайпа / матча. |
| 9 | `services/profiles/src/main/java/com/tinder/profiles/config/mtls/` | Внутренний порт профилей — mTLS, не user JWT. |
| 10 | `scripts/validate-compose-mtls-mounts.rb` | CI проверяет, что Compose реально монтирует сертификаты. |
| 11 | `docs/demo/seed-notes.md` | SQL-seed профиля не создаёт `profile.created` → Deck Read остаётся `202`. |
| 12 | `docs/demo/decisions.md` | Если спросят «почему не проще» — читай отклонённые варианты, не выдумывай. |

Три GitHub-истории (#35 CQRS, #36 outbox, #37 mTLS/лимиты) указывают на те же места. Не учи остальной монорепозиторий «на всякий случай».

### Что не открывать на интервью

| Путь | Почему |
|---|---|
| `services/swipes-demo` | Java-откат. Демо и Compose идут в `services/swipes-go`. |
| `services/moderation` | Сервис в стеке есть; Phase 5 (живые провайдеры / релиз) не защищаем. |
| `docs/architecture/service-refactor-plan.md` | Исторический план. Deck Read и Location **уже** отдельные сервисы. |
| `docs/backend-overview-agent-prompt.md` | Подсказка для агентов, факты устаревают. |
| `docs/features/**` кроме `deck-read-cqrs` | Журнал фич и багов, не рассказ для интервью. |
| Ranking admin / popularity | В репо есть; на живом Discover это не история. |

Eureka и Config Server **удалены** из репозитория. Пиры резолвятся через `*_SERVICE_URL`. Если спросят «а где service discovery?» — «статические URL в Compose; Eureka была leftover и её вырезали, чтобы не объяснять выключенный сервер».

### Честная рамка, если спросят «ты это всё написал?»

Не надо изображать наизусть каждый адаптер Photos/Go/Quarkus. Рабочая фраза:

«Я защищаю matching-путь и три границы: read-model колоды, transactional outbox, JWT на gateway против mTLS на внутреннем fanout. Photos, location и moderation живут отдельно, потому что у них другой отказ. Могу пройти UI за шесть минут или эти классы за двадцать.»

Это сильнее, чем список из пятнадцати имён сервисов.

### Что этим PR не делается

Схлопывать платформу в один Spring-монолит. Тогда нечего защищать в историях #35–#37. Чистим то, что нельзя честно объяснить (мертвый discovery/config) и оставляем карту того, что можно.

---

## English

### Why this file exists

The repo is large because Discover, swipe writes, photos, and billing fail differently. That is only an interview asset if you can defend the **matching path**. Everything else gets one sentence: it is separate because it fails separately.

### Read in this order (an evening, not a week)

1. Root [README.md](../../README.md) — first screen and the six-minute path. Do not memorise Key Endpoints.
2. This file.
3. [script.md](script.md) — what to say with the UI open.
4. [decisions.md](decisions.md) — rejected alternatives.
5. The twelve files in the table above — open each and say out loud what it does.
6. [talk-track.md](talk-track.md) — interruptions.

### The one product path

Two prepared accounts → Discover (`GET /api/v2/deck`) → mutual like → match → text chat.

The browser talks only to the gateway. Cards come from Deck Read. Order is built by `services/deck`. Swipes are written by `services/swipes-go`. A match appears from the outbox, not from “we also called Kafka in the HTTP thread.”

### Honest frame if they ask who wrote it

Do not recite every Photos/Go/Quarkus adapter. Use:

“I defend the matching path and three boundaries: the deck read model, the transactional outbox, and gateway JWT versus mTLS on internal fanout. Photos, location, and moderation are separate because they fail differently. I can walk the UI in six minutes or those classes in twenty.”

That beats a list of fifteen service names.

### What this cleanup does not do

Collapse the platform into one Spring monolith. That would delete stories #35–#37. We removed wiring you cannot honestly explain (Eureka / Config Server) and mapped the code you can.
