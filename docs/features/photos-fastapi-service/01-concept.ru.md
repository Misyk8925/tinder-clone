# Концепт: сервис фотографий на FastAPI

Статус: РЕАЛИЗОВАН · Slug: `photos-fastapi-service`

## 1. Проблема

Загрузка фотографий сейчас живёт внутри Profiles (варианты, S3, ключи) и отдельно в Match (один исходный файл, другой layout ключей). Это две копии медиа-логики. Нужен один внутренний сервис на FastAPI, который повторяет текущую обработку Profiles photos и вызывается и из Profiles, и из Match.

## 2. Контракты и поведение (простым текстом)

Вход: байты изображения, content-type, `owner_id`, необязательный `namespace`. Выход: id хранения, четыре JPEG-URL, ключ original, размер, ширина/высота, sha256. Гарантия: jpeg/png/webp, не больше 5 MB, сторона 300–4096 px, четыре варианта original/large/medium/small. Не делает: JWT, слоты альбома, каталог в Postgres, события колоды, доступ к чату.

Публичные API клиентов не меняются.

## 3. Функциональные требования

| ID | Требование | Как проверяем |
|---|---|---|
| FR-1 | Принимать jpeg/png/webp и отклонять другой тип | pytest `test_policy` / `test_api` |
| FR-2 | Отклонять файл больше 5 MB | pytest `test_policy` |
| FR-3 | Отклонять нечитаемые байты как Corrupted image | pytest `test_service` |
| FR-4 | Отклонять сторону < 300 px или > 4096 px | pytest `test_policy` / `test_service` |
| FR-5 | Писать четыре JPEG-варианта и вернуть URL | pytest `test_service` / `test_api` |
| FR-6 | Удалять все варианты по storage id | pytest `test_api` delete |
| FR-7 | Выдавать presigned URL выбранного размера | pytest `test_api` download-url |
| FR-8 | Удалять orphan-объекты, которых нет в переданном каталоге | pytest `test_service` / `test_api` cleanup |
| FR-9 | Profiles по-прежнему проверяет слоты и пишет каталог, а байты отдаёт сервису | `UploadPhotoServiceTest` |
| FR-10 | Match сохраняет вложение из ответа сервиса | `ConversationPhotoStorageServiceTest` |

## 4. Нефункциональные требования

| ID | Требование | Как измеряем |
|---|---|---|
| NFR-1 | Таймаут клиента 30 s на upload 5 MB | `PhotosClientConfig` / `PhotosServiceAdapter` |
| NFR-2 | Health `GET /health` → `{"status":"UP"}` | pytest + compose healthcheck |
| NFR-3 | Сервис внутренний, без JWT | нет маршрута в gateway |
| NFR-4 | Нет PII в S3 metadata кроме owner/timestamps | `x-origin`, `uploaded-at` |

## 5. Вне рамок

- Публичный клиентский API на FastAPI
- Перенос таблицы `photos` из Profiles
- Локальный fallback S3 в Java
- Миграция уже лежащих chat-объектов со старыми ключами

## 6. Предлагаемое решение

База: один FastAPI-процесс, как location-go. Profiles оставляет слоты/каталог/события. Match оставляет доступ к разговору и запись Message. Оба ходят HTTP на `:8070`.

Отклонено: оставить обработку в Profiles и только проксировать Match — тогда логика Profiles не вынесена. Отклонено: dual-write/local fallback — это оставляет две реализации.

## 7. Открытые вопросы

Нет. Запрос явно зафиксировал FastAPI, полное повторение Profiles photos и интеграцию в оба сервиса.
