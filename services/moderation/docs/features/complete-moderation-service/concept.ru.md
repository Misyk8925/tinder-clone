# Концепт: законченный moderation-service

Статус: **УТВЕРЖДЁН 2026-09-06**  
Дата: 2026-09-06 · Slug: `complete-moderation-service`

## 1. Проблема

В репозитории уже есть доменный pipeline модерации, нейтральная предобработка,
evidence и versioned Kotlin policy DSL. Однако приложение ещё нельзя использовать как
самостоятельный сервис: нет входного API и consumer, реальных provider adapters,
хранилища решений, runtime-управления policy, review workflow и операционного интерфейса.

Из-за этого клиент не получает синхронное решение, опубликованную policy нельзя сменить
без редеплоя, а спорное решение нельзя объяснить или проверить модератором после рестарта.

## 2. Контракты и поведение простым текстом

На входе:

- синхронный запрос с контентом и ограниченным контекстом;
- versioned Kafka event с теми же бизнес-данными;
- действия администратора над policy и review task.

На выходе:

- `ALLOW`, `FLAG`, `BLOCK` или `HOLD` вместе с evidence и применённой policy;
- сохранённое, повторяемое и объяснимое решение;
- versioned result event для асинхронных потребителей.

Система гарантирует:

- слова, URL и regex сами по себе не создают content block;
- malformed, oversized, flood и rate-limit останавливаются до classifier;
- classifier и LLM получают нормализованный текущий контент;
- LLM дополнительно получает ограниченный caller-supplied context, classifier result и applied policy;
- timeout, неизвестная категория или отсутствующая policy не превращаются в `ALLOW`;
- опубликованная policy неизменяема, а activation и rollback не требуют редеплоя;
- повтор REST-команды или Kafka event не создаёт второе независимое решение;
- административные изменения и review actions попадают в audit log.

Система не делает:

- не загружает conversation context из других сервисов через `LlmPort`;
- не управляет пользователями и не интегрируется с Keycloak/OIDC;
- не предоставляет внешний пользовательский moderation UI;
- не меняет `match`, `profile` или другие sibling services;
- не выполняет production deployment в рамках реализации модуля.

## 3. Функциональные требования

| ID | Требование | Как проверяем |
|---|---|---|
| FR-1 | Сервис принимает синхронный moderation request через versioned REST API. | API acceptance test проверяет полный ответ и сохранённое решение. |
| FR-2 | Сервис принимает versioned moderation events из Kafka с at-least-once delivery. | Integration test отправляет event и читает result event. |
| FR-3 | Сервис валидирует, нормализует и rate-limit-ит payload до вызова classifier. | Tests доказывают вызов или отсутствие вызова provider для каждой ветки. |
| FR-4 | Сервис передаёт classifier полный `ModerationContent`. | Contract test фиксирует text, images, type и locale. |
| FR-5 | `LlmPort` принимает current content, bounded conversation context, application signals, classifier result и applied policy. | Port contract test сравнивает весь request без потери полей. |
| FR-6 | Сервис получает context только из входной команды и не загружает его внутри `LlmPort`. | Architecture/unit test использует чистый recording adapter. |
| FR-7 | Domain policy выдаёт решение по category thresholds, rule hits и adjudication с полным evidence. | Existing и новые boundary/conflict tests. |
| FR-8 | Сервис сохраняет request identity, provider/model provenance, scores, context references, rule hits, applied policy, decision и timestamps. | PostgreSQL round-trip integration test. |
| FR-9 | Повтор с тем же idempotency key или event id возвращает существующий результат. | REST и Kafka duplicate tests. |
| FR-10 | Policy API создаёт draft, валидирует его, публикует immutable version и читает историю. | API tests для happy path и каждого запрещённого transition. |
| FR-11 | Policy API активирует и откатывает policy для scope `contentType + locale` без рестарта. | Integration test меняет active version и получает другое решение в том же процессе. |
| FR-12 | Policy API предоставляет preview решения без публикации draft. | API test доказывает preview и отсутствие persisted moderation decision. |
| FR-13 | Сервис создаёт review task для `FLAG` и `HOLD` согласно policy/failure reason. | Persistence и event acceptance tests. |
| FR-14 | Модератор подтверждает, отменяет или эскалирует review task с optimistic locking. | Concurrent update acceptance test. |
| FR-15 | Internal admin GUI показывает decisions, evidence, provenance и review queue с фильтрацией. | Server-rendered MVC tests и browser smoke. |
| FR-16 | Policy admin создаёт, сравнивает, валидирует, публикует, активирует и откатывает policy через GUI. | MVC acceptance tests и browser smoke. |
| FR-17 | Spring Security защищает GUI form session, а internal API — HTTP Basic с configured BCrypt users. | Security tests для anonymous, role mismatch, CSRF и valid role. |
| FR-18 | Сервис публикует result/review/policy events через transactional outbox. | Restart и retry integration tests доказывают доставку без потери. |
| FR-19 | Сервис сообщает health, readiness и moderation metrics. | Management endpoint и meter registry tests. |

## 4. Нефункциональные требования

| ID | Требование с числом | Как измеряем |
|---|---|---|
| NFR-1 | p95 REST latency не выше 2 секунд при 50 RPS, когда provider отвечает не медленнее 1 секунды. | Локальный load test с provider stub. |
| NFR-2 | Provider timeout ограничен 1500 мс; исчерпание retry даёт `HOLD`, а не `ALLOW`. | Failure injection test с виртуальным/stub delay. |
| NFR-3 | Context содержит не более 20 сообщений и не более 16 KiB текста суммарно. | Boundary tests на API и event contracts. |
| NFR-4 | REST request body не превышает 1 MiB; остальные content limits задаются policy/config. | Boundary tests на HTTP edge. |
| NFR-5 | Duplicate REST/event processing создаёт ровно одну decision record и не более одного логического result event. | Concurrency test минимум с 20 одновременными дублями. |
| NFR-6 | Published policy и moderation evidence сохраняются без потери при рестарте. | Testcontainers restart/reload test. |
| NFR-7 | Raw content, credentials и provider payload не попадают в application logs. | Log-capture security tests по marker values. |
| NFR-8 | Пароли хранятся только как BCrypt hash; session cookie имеет HttpOnly и SameSite=Lax, а Secure включается вне local profile. | Configuration/security tests. |
| NFR-9 | После 5 неудачных login попыток пользователь блокируется на 15 минут. | Deterministic security test с injected clock. |
| NFR-10 | Audit log фиксирует actor, action, target, timestamp и outcome для 100% policy/review mutations. | Acceptance tests сверяют каждую mutation с audit record. |
| NFR-11 | Raw content и переданный context по умолчанию удаляются через 90 дней; агрегированное evidence остаётся по отдельной retention policy. | Repository cleanup test с injected clock; срок подтверждается перед release. |
| NFR-12 | Kafka consumer поддерживает at-least-once delivery и отправляет poison event в DLQ после 5 неуспешных попыток. | Embedded/Testcontainers Kafka retry and DLQ tests. |

## 5. Вне рамок

- Keycloak, OIDC и внешний identity provider.
- Управление internal users через GUI или API.
- Отдельный SPA/frontend repository.
- Изменение producer/consumer кода в sibling services.
- Appeals UI для конечного пользователя.
- Production infrastructure, секреты и deployment.
- Обучение собственной ML-модели.

## 6. Предлагаемое решение

### Диаграмма

```text
REST request ─┐                         ┌─> result response/event
Kafka event ──┼─> inbound adapters ─> moderation pipeline
Admin GUI/API ┘          │              │
                         ├─> classifier/LLM adapters
                         ├─> PostgreSQL: policy, evidence, review, audit, outbox
                         └─> Redis: rate limit, duplicate/cache acceleration
```

### Компоненты

| Компонент | Ответственность |
|---|---|
| REST adapter | Синхронная moderation и internal Policy/Review API. |
| Kafka adapter | Idempotent inbound consumer и versioned outbound events. |
| PreModerationProcessor | Техническая validation, normalization, traffic control и metadata signals. |
| ModerateContentUsecase | Оркестрация classifier, evidence, optional LLM adjudication и policy. |
| Provider adapters | Реализация vendor-neutral classifier/LLM ports с timeout и resilience. |
| Policy service | Draft, validation, immutable publication, activation, resolution и rollback. |
| Review service | Очередь, manual decision, escalation и optimistic locking. |
| Admin MVC | Server-rendered internal UI без отдельного frontend runtime. |
| PostgreSQL | Source of truth для policy, decisions, evidence, review, audit и outbox. |
| Redis | Rate limit, duplicate suppression и cache; не source of truth. |
| Outbox worker | Надёжная публикация событий после commit. |

### Основной поток

1. Adapter аутентифицирует internal actor или принимает trusted Kafka event.
2. Adapter валидирует envelope и строит `ContentCmd` с caller-supplied context.
3. Preprocessor нормализует content и отклоняет только технически невалидный или throttled request.
4. Classifier возвращает category scores с provider/model provenance.
5. Policy определяет, нужна ли дополнительная adjudication.
6. Use case передаёт `LlmAnalysisRequest` вместе с bounded context в `LlmPort`.
7. Domain service применяет активную immutable policy и сохраняет полный evidence.
8. В одной транзакции создаются decision, при необходимости review task, audit и outbox records.
9. REST возвращает решение; outbox worker публикует versioned event.

### Данные и хранение

PostgreSQL содержит policy drafts/versions/activations, moderation decisions,
evidence, review tasks, audit entries, processed-message keys и outbox. Published policy
не обновляется: rollback меняет только active pointer. Evidence хранит provider/model/version
и исходные category scores раздельно. Redis ускоряет rate limiting и повторные чтения, но
потеря Redis не уничтожает durable decision или policy.

### Внешние системы

- Kafka для versioned moderation commands/results и DLQ.
- Настраиваемый classifier/LLM provider через vendor-neutral ports.
- PostgreSQL и Redis.
- Никакого Keycloak или OIDC.

### Поведение при сбоях

- Нет policy или provider вернул неизвестную обязательную категорию: `HOLD`.
- Provider timeout/temporary failure: bounded retry, затем `HOLD` и review task.
- PostgreSQL недоступен: mutation не подтверждается и возвращает retryable service error.
- Redis недоступен: durable flow продолжает работать там, где это безопасно; rate-limit
  переходит в локально ограниченный fallback и создаёт degraded metric.
- Kafka publish failure: outbox остаётся pending и повторяется worker-ом.
- Poison Kafka event: после пяти попыток отправляется в DLQ с correlation metadata.
- Одновременное review/policy изменение: проигравший получает conflict и перечитывает state.

### Аутентификация

Spring Security читает internal users и BCrypt hashes из external configuration.
Admin GUI использует form login и server-side session. Internal API использует HTTP Basic.
Роли: `VIEWER`, `MODERATOR`, `POLICY_ADMIN`. CSRF включён для browser mutations.
Service должен быть закрыт network policy и не публиковаться напрямую в интернет.

### Отклонённые альтернативы

Выбранная база — один Spring Boot service с server-rendered GUI и существующим Kotlin core.

| Вариант | Почему не взяли | Связанный FR/NFR |
|---|---|---|
| Kafka-only | Не даёт клиенту синхронного решения. | FR-1, NFR-1 |
| Только REST | Не закрывает replay, delayed work и event integration. | FR-2, FR-18, NFR-12 |
| In-memory policy/evidence | Теряет audit и state при рестарте; policy activation нельзя доказать. | FR-8, FR-11, NFR-6 |
| Отдельный SPA | Добавляет второй build/runtime без отдельного UX-требования. | FR-15, FR-16 |
| Keycloak/OIDC | Владелец явно выбрал simple internal auth. | FR-17 |
| Выполнение Kotlin scripts в runtime | Усложняет sandboxing и безопасность; типобезопасный DSL остаётся seed/test layer, runtime state хранится как validated data. | FR-10, FR-11, NFR-7 |
| AWS и OpenAI одновременно в первой версии | Не требуется ни одним утверждённым FR; vendor-neutral ports позволяют добавить provider позже. | FR-4, FR-5 |

## 7. Открытые вопросы

Открытые вопросы и риски ведутся в [`00-state.md`](00-state.md). Они не требуют
угадывать контракты: provider/model, production retention и Kafka deployment names
останутся configurable или release-gated.
