# Log: photos-fastapi-service

## 2026-08-21 — slices 1–3

Implemented FastAPI photos service with in-memory storage in tests, then pointed Profiles `PhotoMediaPort` and Match `ConversationPhotoStorageService` at it.

Match conversation photos now follow Profiles processing: JPEG variants and 300–4096 px. Old chat objects in S3 are unchanged; new uploads use `chat/photos/{conversationId}/{storageId}/{variant}.jpg`.
