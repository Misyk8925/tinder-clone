# Six-minute demo script

Goal: a recruiter sees a working matching journey. A Java interviewer can interrupt and get a real answer from [talk-track.md](talk-track.md).

Primary surface: **https://lunari.misyk.tech** (only after [live-stand.md](live-stand.md) preflight is green).
Backup: the recorded walkthrough, or Angular `start:preview` for UI-only.

Do not sign up live. Do not open Stripe. Do not open admin.

---

## Русский (6 минут)

### 0:00–0:30 — рамка

«Это не клон ради клона. Продуктовый путь — знакомство, свайп, матч, чат. Инженерная задача — чтобы колода оставалась быстрой, а матч не терялся, когда запись, чтение и события живут в разных сервисах.»

Открыть Lunari. Коротко: Java 21, Spring, Quarkus, Kafka, Redis, Keycloak; рядом Go и FastAPI.

### 0:30–1:15 — логин A

Войти заранее подготовленным аккаунтом A. Не регистрироваться.

«Клиент ходит только в gateway. JWT выпускает Keycloak. Внутренние вызовы профилей и истории свайпов — отдельные mTLS-порты.»

### 1:15–2:30 — Discover

Открыть Discover. Дождаться карточки.

«Чтение колоды — `GET /api/v2/deck` в Deck Read (Quarkus). Это read-model: порядок и карточки уже материализованы. Если колоды нет, Deck Read зовёт `ensure` на write-side (`services/deck`), а не строит выдачу синхронно из Profiles.»

Свайпнуть вправо (like). Не объяснять ranker.

Если сразу всплыло **It's a match** — это взаимный лайк: клиент после свайпа ещё раз читает `GET /match/{me}`, потому что тело свайпа пустое, а матч появляется из outbox. «Send a message» открывает чат.

Запасная фраза, если колода пустая: «Пара уже разобрана или read-model ещё догоняет событие профиля. Открою готовый матч.» → Matches.

Запасная фраза, если 202 / “preparing”: «Write-side ещё кладёт порядок в Redis, клиент честно ждёт. Это лучше, чем отдать пустой список.»

### 2:30–4:00 — взаимный лайк и матч

Второе окно / инкогнито: аккаунт B. Лайкнуть A (или показать уже готовый матч, если времени мало).

«Свайп пишется в swipes-go. Consumer ищет взаимность. Match и swipe-события уходят не best-effort в Kafka, а через transactional outbox: строка в той же транзакции, потом batch publisher, retry, dead-letter.»

Если оверлей уже на экране — «Send a message». Иначе открыть появившийся матч в Matches.

### 4:00–5:15 — чат

Открыть переписку. Отправить одно текстовое сообщение. Фото не отправлять.

«Match-сервис владеет разговором. WebSocket идёт через gateway с отдельным allow-list origin. Медиа — другой сервис; в демо достаточно текста.»

### 5:15–6:00 — чем закончить

Вернуться к схеме в README.

«Если пойдём вглубь, у меня три истории: CQRS колоды, outbox, границы безопасности. Модерация и ranking experiments в репозитории есть, в демо-контуре сознательно baseline.»

Опционально 20 секунд: вкладка Likes у premium-аккаунта. Не открывать Checkout.

---

## English (6 minutes)

### 0:00–0:30 — frame

“This is a matching product, not a framework zoo. The user journey is discover, swipe, match, chat. The engineering problem is keeping deck reads fast without dropping a match when write, read, and side effects live in different processes.”

Open Lunari. Stack in one breath: Java 21, Spring, Quarkus, Kafka, Redis, Keycloak; Go and FastAPI at the edges.

### 0:30–1:15 — sign in A

Sign in with prepared account A. Do not register.

“The browser talks only to the gateway. Keycloak issues the JWT. Profile and swipe-history calls that carry other people’s data use separate mTLS listeners.”

### 1:15–2:30 — Discover

Open Discover. Wait for a card.

“The client reads `GET /api/v2/deck` from Deck Read. That service owns the read model. On a miss it calls `ensure` on the Deck write side instead of joining Profiles live.”

Swipe right.

If **It's a match** appears, that is a mutual like. The swipe response is empty; Discover re-reads `GET /match/{me}` and opens chat from Send a message.

If the deck is empty: “This pair is already consumed, or the projection has not caught `profile.created`. I’ll open a prepared match.”

If the UI stays on “preparing”: “The write side is still materialising order. Returning 202 is better than a silent empty deck.”

### 2:30–4:00 — mutual like

Second browser: account B likes A, or open an existing match.

“Swipes land in swipes-go. The consumer detects reciprocity. Swipe and match events are written to an outbox in the same transaction, then a batch publisher retries and dead-letters instead of fire-and-forget Kafka.”

### 4:00–5:15 — chat

Open the conversation. Send one text message. No photo.

“Match owns conversations. The WebSocket origin allow-list is explicit. Photos are a different service; text is enough here.”

### 5:15–6:00 — close

Back to the architecture image.

“The three interview stories are CQRS for the deck, the outbox, and the security boundary. Ranking experiments and moderation exist in the repo; the demo contour stays on the baseline ranker.”

Optional: Likes You on an account that already has `USER_PREMIUM`.

---

## Recording notes

Record this same path. Face is optional. End with 30 seconds on one class:

- `ProfileOutboxBatchProcessor` or `SwipeOutboxEventDispatcher`, or
- `DeckReadCqrsBoundaryAcceptanceTest`

If the public origin is down, record Angular design-preview (`npm run start:preview`) for the UI, then cut to those two files. In preview, like Mila: the fixture already lists her as a match, so the overlay appears. Say clearly that preview is a fixture client, not the Kafka path. Two live accounts on a real stand are what make the overlay wait on the outbox.

Publish an unlisted YouTube or Loom link and put it in the root README before sending the repo to recruiters. The pull request walkthrough is only an internal backup.
