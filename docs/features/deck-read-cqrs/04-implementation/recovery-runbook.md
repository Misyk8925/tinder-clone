# Deck Read Read Cluster recovery runbook

## Что такое recovery drill

Это контролируемая репетиция потери Read Cluster: на тестовом окружении удаляют или изолируют только его данные, проверяют безопасный `503 READ_MODEL_NOT_READY`, восстанавливают projection по процедуре ниже и доказывают, что API становится READY только после проверок. Это не авария в production и не автоматическое удаление production volumes.

## Источники восстановления

1. Первый выбор — последний проверенный snapshot/backup шести Redis volumes, если deployment его предоставляет.
2. Если backup отсутствует или невалиден — Profiles PostgreSQL backfill восстанавливает полные cards и user mapping.
3. Kafka догоняет изменения и восстанавливает только доступное retention-окно swipe/match. Kafka не обязана хранить всю историю навсегда.
4. Existing Deck Redis повторно даёт ordered profile IDs при request-driven snapshot build.

Важно: `auto.offset.reset=earliest` применяется только к consumer group без committed offsets. Обычные группы Deck Read уже имеют offsets, поэтому простого рестарта и проверки `lag=0` недостаточно для восстановления потерянных Redis mutations.

## Безопасная последовательность

1. Остановить клиентский cutover или убедиться, что `DECK_READ_REQUIRE_READY_MARKER=true`. В потерянном/новом cluster ключ `dr:read-model:ready` отсутствует, поэтому v2 и v1 не должны выдавать ложный EMPTY.
2. Проверить Redis Cluster: `cluster_state:ok`, ожидаемое число masters/replicas, распределение всех 16384 slots, AOF/RDB состояние и отсутствие eviction.
3. Если восстанавливается backup, проверить его дату и consistency; не выставлять readiness только потому, что Redis запустился.
4. Сгенерировать один recovery `runId`, остановить все обычные Deck Read replicas и запустить recovery replica с уникальными group IDs для истории swipe/match:

   ```bash
   DECK_READ_SWIPE_GROUP_ID=deck-read-swipe-recovery-<runId>
   DECK_READ_MATCH_GROUP_ID=deck-read-match-recovery-<runId>
   ```

   Новые группы благодаря `auto.offset.reset=earliest` читают `swipe-saved` и `match.created` с начала доступного Kafka retention. Не переиспользовать group IDs прошлой recovery: у них уже есть committed offsets. Profile group можно оставить штатной — полный Profiles backfill ниже создаёт новые outbox events в конце topic. Альтернатива с reset offsets допустима только при остановленных consumer replicas и отдельном change approval; рекомендуемый путь — новые recovery groups, потому что он не переписывает рабочие offsets.
5. С тем же `runId` через доверенный internal certificate вызвать:

   ```bash
   curl --fail-with-body --cert <internal-client-cert.pem> --key <internal-client-key.pem> \
     -X POST "https://profiles:8011/api/v1/profiles/internal/deck-card-projection/backfills/<runId>"
   ```

   После timeout/restart повторять строго тот же URI и тот же runId. Status:

   ```bash
   curl --fail-with-body --cert <internal-client-cert.pem> --key <internal-client-key.pem> \
     "https://profiles:8011/api/v1/profiles/internal/deck-card-projection/backfills/<runId>"
   ```

6. Дождаться `COMPLETED`. `ENQUEUED` ещё недостаточно: outbox publisher может не отправить все rows.
7. Проверить нулевой lag именно recovery swipe/match groups и штатной profile group; проверить DLT. Нулевой lag означает только «consumer дошёл до head» и не заменяет count/retention проверки.
8. Сравнить Profiles counts с read model: обработанное число run, количество profile card hashes, user mappings и tombstones. Выборочно сравнить версии и полные cards.
9. Проверить границу retention: earliest доступный offset swipe/match должен покрывать требуемые семь дней. Если окно короче или completeness не доказана, ключ `dr:read-model:repeat-ready` не выставлять: repeat остаётся выключенным на семидневный warm-up. Fresh можно включить отдельно после card/count/lag verification.
10. Проверить несколько viewer IDs: existing Deck ordering импортируется, deleted/matched/swiped не возвращаются во fresh, generation растёт монотонно; повторные cards появляются только через repeat projection и только после fallback threshold.
11. Остановить recovery replica и вернуть штатные group IDs. Запустить обычные Deck Read replicas: они дочитают события, накопившиеся после их остановки; повторная доставка безопасна благодаря idempotent materializers.
12. После проверки profile projection записать `dr:read-model:ready=READY` в Read Cluster — это открывает fresh reads. Только после доказанной полноты семидневной swipe/match истории отдельно записать `dr:read-model:repeat-ready=READY`. Сам сервис эти production recovery gates автоматически не открывает.

## Drill acceptance

- До readiness v1/v2 возвращают `503 READ_MODEL_NOT_READY`, а не пустую колоду.
- При `dr:read-model:ready=READY` и отсутствующем `dr:read-model:repeat-ready` fresh работает, но degraded repeat не запрашивается и не выдаётся.
- Повтор POST с тем же runId не дублирует committed page и продолжает после durable cursor.
- Старый backfill event не откатывает более новую LIVE version.
- После READY cards не исчезают через семь дней: card projection не имеет TTL.
- Match/delete не появляются ни во fresh, ни в repeat; incomplete history не включает repeat.
- Потеря одного replica восстанавливается без backfill; полная потеря cluster требует verified backup или процедуру выше.

## Автоматизированная часть drill

Репозиторий проверяет последовательность двумя связанными integration boundaries:

- `JpaDeckCardProjectionBackfillAdapterIntegrationTest` использует PostgreSQL Testcontainers и доказывает, что `FAILED` run повторно запускается с тем же `runId`, durable cursor и `processedCount`; конкурентный другой run сериализуется.
- `DeckReadKafkaRedisRuntimeAcceptanceTest` использует реальные Kafka и Redis: первая BACKFILL delivery материализуется при NOT_READY, повтор той же delivery и продолжение с тем же `runId` идемпотентны, card/mapping counts совпадают, consumer lag равен нулю, и только затем операторские markers открывают fresh и repeat отдельно.

Это не подменяет deployed mTLS вызов controller: сертификат, endpoint, Kafka retention и backup restore проверяются в конкретном deployment environment по шагам выше.
