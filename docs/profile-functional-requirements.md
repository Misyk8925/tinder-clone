# Profile Service — Functional Requirements (FR) & TDD TODO

> Область: только функциональные требования сервиса профилей и их задачи для TDD.  
> Вне области: инфраструктура (Eureka/Config/Gateway), CI/Observability, перфоманс и т.п.

## Нотация
- **API**: контракт конечной точки.
- **Валидация**: правила входных данных.
- **Ответ**: успешный минимальный ответ.
- **Ошибки**: важные коды/кейсы.
- **BDD**: критерии приемки в формате Given/When/Then.
- **TDD TODO**: перечень тестовых задач (красные тесты → реализация → рефакторинг).

---

## FR-1. Create Profile — `POST /api/v1/profiles`
**Цель.** Пользователь создаёт профиль с базовыми полями.

**API**
- Request (JSON): `{ name, age, bio?, location? }`
- Headers: `X-User-Id: <uuid>`
- Response 201 (JSON): `{ profileId, userId, name, age, bio, location, createdAt }`

**Валидация**
- `name`: 1..100 символов
- `age`: целое, >= 18
- `bio`: 0..500 символов
- `location`: опционально (строка/структура по выбору), при наличии — непустая
- Пользователь определяется по `X-User-Id`

**Ошибки**
- 400: невалидные поля
- 409: профиль уже существует у данного `userId` (если бизнес-правило уникальности включено)

**BDD**
```
Given валидный X-User-Id и тело запроса {name, age>=18, bio<=500, location?}
When POST /api/v1/profiles
Then статус 201 и тело содержит profileId, userId и сохранённые поля
And запись создана в БД
```

**TDD TODO**
- Red: unit-тесты валидации и доменной логики (возраст, длины полей, уникальность при необходимости)
- Red: API-контракт (JSON schema, статус 201, обязательные поля)
- Red: интеграционные тесты (JPA/репозиторий) с Testcontainers (Postgres)
- Red: негативные кейсы 400/409
- Green: минимальная реализация
- Refactor: слои/мапперы/фикстуры данных
- Обновить OpenAPI (пример запроса/ответа)

---

## FR-2. Get Profile by ID — `GET /api/v1/profiles/{id}`
**Цель.** Получить профиль по идентификатору.

**API**
- Path: `id` — UUID/число (в зависимости от модели)
- Response 200: объект профиля

**Ошибки**
- 404: профиль не найден или помечён как удалённый

**BDD**
```
Given существующий profileId
When GET /api/v1/profiles/{id}
Then статус 200 и JSON соответствует схеме профиля
```

**TDD TODO**
- Red: репозиторий — поиск по id
- Red: API-контракт 200/404
- Red: кейс скрытия мягко удалённых записей
- Green, Refactor, OpenAPI

---

## FR-3. Update Profile — `PUT /api/v1/profiles/{id}`
**Цель.** Редактирование текстовых полей профиля.

**API**
- Request JSON: подмножество { name, age, bio, location }
- Headers: `X-User-Id`
- Response 200: обновлённый профиль

**Валидация**
- Правила как в FR-1
- Проверка владения: `profile.userId == X-User-Id`

**Ошибки**
- 400: невалидные поля
- 403: попытка правки чужого профиля
- 404: профиль не найден/удалён

**BDD**
```
Given профиль принадлежит пользователю по X-User-Id
When PUT /api/v1/profiles/{id} с валидными полями
Then статус 200 и поля обновлены в БД
```

**TDD TODO**
- Red: unit для проверки владения и валидации
- Red: API-контракт 200/400/403/404
- Red: интеграционный тест обновления
- Green, Refactor, OpenAPI

---

## FR-4. Soft Delete Profile — `DELETE /api/v1/profiles/{id}`
**Цель.** Мягкое удаление профиля (скрывать из выдач, не терять данные).

**API**
- Headers: `X-User-Id`
- Response 204: без тела

**Правила**
- Устанавливать `isDeleted = true`
- Исключать такие записи из всех выборок

**Ошибки**
- 403: чужой профиль
- 404: профиль не найден

**BDD**
```
Given профиль существует и принадлежит пользователю
When DELETE /api/v1/profiles/{id}
Then статус 204 и isDeleted=true в БД; повторные GET не возвращают запись
```

**TDD TODO**
- Red: интеграционный тест удаления и последующего скрытия
- Red: 403/404 сценарии
- Green, Refactor, OpenAPI

---

## FR-5. Discover by Age Range & Pagination — `GET /api/v1/profiles?ageMin&ageMax&page&size`
**Цель.** Возвращать список профилей по возрастному диапазону с пагинацией.

**API**
- Query: `ageMin`, `ageMax`, `page`, `size`
- Response 200: `{ items: [...], page, size, total }`
- Исключать `isDeleted=true`

**Ошибки**
- 400: некорректные диапазоны/параметры пагинации

**BDD**
```
Given в БД есть анкеты разных возрастов
When GET /api/v1/profiles?ageMin=25&ageMax=35&page=0&size=20
Then 200; все профили соответствуют диапазону; пагинация корректна; удалённые исключены
```

**TDD TODO**
- Red: unit-логика фильтрации возраста
- Red: интеграционный тест pageable-выдачи (с фикстурами разного возраста)
- Red: негативные 400 (ageMin > ageMax и т.п.)
- Green, Refactor, OpenAPI

---

## FR-6. Discover by Distance (опционально) — `GET ...&maxDistanceKm&userLat&userLon`
**Цель.** Возвращать профили в радиусе от позиции пользователя (Haversine).

**API**
- Query: `maxDistanceKm`, `userLat`, `userLon`
- Response 200: список профилей в пределах радиуса

**Ошибки**
- 400: отсутствуют/некорректны координаты

**BDD**
```
Given у профилей есть координаты; у запроса есть userLocation
When GET ...?maxDistanceKm=10&userLat=...&userLon=...
Then 200; в ответе только профили в радиусе 10 км
```

**TDD TODO**
- Red: модульный расчёт расстояния (Haversine) и пограничные значения
- Red: интеграционный тест фильтрации по радиусу
- Red: негативные 400 (неполные координаты)
- Green, Refactor, OpenAPI

---

## FR-7. Exclude List & Limit — `GET ...&exclude=...` (список userId)
**Цель.** Исключать ранее показанные/нежелательные профили, ограничивать размер ответа.

**Правила**
- `exclude`: список `userId`, которые не должны попадать
- Максимум 20 результатов в одном ответе

**BDD**
```
Given exclude содержит список userId
When GET /api/v1/profiles?exclude=...&size=20
Then 200; ни один исключённый не возвращается; максимум 20 результатов
```

**TDD TODO**
- Red: unit для исключений по списку
- Red: интеграционный тест лимита 20 и корректной пагинации
- Green, Refactor, OpenAPI

---

## FR-8. Photo Upload (опционально) — `POST /api/v1/profiles/{id}/photos`
**Цель.** Загрузка 1–5 фото (JPEG/PNG) для профиля, хранение по внешнему объектному хранилищу.

**API**
- Multipart: файл `JPEG/PNG` ≤ 5MB
- Ограничение: максимум 5 фото на профиль
- Response 201: `{ photoId, isPrimary, url | signedUrl }`

**Ошибки**
- 400: неверный формат/размер файла; превышен лимит количества
- 403: профиль не принадлежит пользователю
- 404: профиль не найден

**BDD**
```
Given профиль принадлежит пользователю; файл JPEG/PNG<=5MB
When POST /api/v1/profiles/{id}/photos
Then 201; возвращены photoId и ссылка; число фото<=5
```

**TDD TODO**
- Red: проверка формата/размера/лимита
- Red: интеграционный тест загрузки (можно с эмулятором/заглушкой)
- Red: негативные 400/403/404
- Green, Refactor, OpenAPI

---

## FR-9. Set Primary Photo (опционально) — `PUT /api/v1/profiles/{id}/photos/{photoId}/primary`
**Цель.** Назначить одно основное фото.

**Правила**
- Ровно одно `isPrimary=true` на профиль

**BDD**
```
Given у профиля ≥1 фото
When PUT .../primary
Then 200; isPrimary=true только у одного фото
```

**TDD TODO**
- Red: инвариант единственности primary
- Red: интеграционные кейсы смены primary
- Green, Refactor, OpenAPI

---

## FR-10. Delete Photo (опционально) — `DELETE /api/v1/profiles/{id}/photos/{photoId}`
**Цель.** Удалить фото профиля.

**BDD**
```
Given фото принадлежит профилю пользователя
When DELETE .../photos/{photoId}
Then 204; фото больше не доступно
```

**TDD TODO**
- Red: удаление + проверка отсутствия в выборках
- Red: негативные 403/404
- Green, Refactor, OpenAPI
