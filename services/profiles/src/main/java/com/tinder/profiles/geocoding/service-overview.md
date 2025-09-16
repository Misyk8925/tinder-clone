Этот метод делает запрос к Nominatim API (OpenStreetMap геокодер), чтобы по названию города вернуть его координаты (долгота/широта). Разберём построчно 👇

⸻

Сигнатура

public Optional<GeoPoint> geocodeCity(String city)

	•	На вход: строка с названием города.
	•	На выход: Optional<GeoPoint> (внутри есть lon/lat, если удалось найти; или Optional.empty(), если не удалось).

⸻

Логика работы
1.	Проверка входа

if (city == null || city.isBlank()) return Optional.empty();

Если строка пустая → сразу вернём пустой результат.

	2.	HTTP GET запрос к Nominatim

nominatimClient.get()
.uri(uri -> uri.path("/search")
.queryParam("q", city.trim())
.queryParam("format", "jsonv2")
.queryParam("limit", 1)
.queryParam("addressdetails", 1)
.queryParamIfPresent("countrycodes", ...)
.build())

	•	q=city → название города.
	•	format=jsonv2 → вернуть JSON.
	•	limit=1 → только первый результат.
	•	addressdetails=1 → включить детали адреса.
	•	countrycodes (если задан) → ограничить поиск конкретными странами.

3.	Отправка запроса и обработка ответа

.accept(MediaType.APPLICATION_JSON)
.retrieve()
.onStatus(status -> status.isError(), response -> response.createException())
.bodyToMono(NominatimResult[].class)

	•	Ждём JSON-массив NominatimResult[].
	•	Если HTTP-ошибка → выбрасываем исключение.

4.	Таймаут и обработка ошибок

.timeout(Duration.ofMillis(timeoutMs))
.onErrorResume(ex -> Mono.just(new NominatimResult[0]))

	•	Если запрос висит дольше timeoutMs → прерываем.
	•	Если любая ошибка (сеть, парсинг и т.д.) → возвращаем пустой массив.

5.	Берём первый элемент, конвертируем в GeoPoint

.map(arr -> arr.length > 0
    ? Optional.of(arr[0].toPoint())
    : Optional.<GeoPoint>empty())

	•	Если массив не пустой → берём arr[0], преобразуем в GeoPoint(lon, lat).
	•	Иначе → Optional.empty().

6.	Если вообще ничего нет

.defaultIfEmpty(Optional.<GeoPoint>empty())

→ fallback на пустой результат.

	7.	Блокируем реактивный поток

.block();

Возвращаем итоговый Optional<GeoPoint> в синхронном виде.

⸻

Итог
•	Если удалось найти город → Optional.of(new GeoPoint(lon, lat)).
•	Если не нашли или ошибка → Optional.empty().

⸻

Хочешь, я покажу, как этот метод можно переписать короче и чище (например через Arrays.stream(arr).findFirst()), чтобы не таскать лишние Optional.empty() в цепочке?