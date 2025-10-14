# Исправления сериализации и десериализации JSON



## 2. Проблема с сериализацией Location
- В класс Location добавлена аннотация `@JsonIgnoreProperties(ignoreUnknown = true)`.
- Это позволяет игнорировать дополнительные поля (например, latitude, longitude) при десериализации из JSON.

## 3. Проблема с Preferences
- Для поля `profiles` в Preferences используется только `@JsonIgnore`, чтобы избежать циклических ссылок.
- Для поля `preferences` в Profile также используется только `@JsonIgnore`.

## 4. Проблема с сохранением зависимостей
- В сервисе ProfileServiceImpl Preferences сохраняется отдельно перед сохранением Profile, чтобы избежать ошибки Hibernate.

## 5. Использование GenericJackson2JsonRedisSerializer
- В конфигурации Redis используется сериализатор с поддержкой type info, чтобы корректно восстанавливать типы объектов из кэша.

---

Все эти изменения обеспечивают корректную работу сериализации/десериализации объектов Profile, Preferences и Location при работе с Redis и кэшем.

