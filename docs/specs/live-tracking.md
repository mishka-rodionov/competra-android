# Онлайн-трекинг участников на соревновании — дизайн-спека

Статус: **брейншторм завершён (23.09.2026), реализация начата с этапа 0**.

Кросс-проектность: `competra-android` (бегун + зритель), `eSport` (основной бэкенд + новый процесс трекинга), `competra-web-ts` (только зритель), `mapper` (привязка карты по трём точкам). Только ориентирование (`OrienteeringCompetition`), циклические события не входят.

Документ заменяет **Часть 2** и раздел «Клиентский рендер» из [`map-and-tracking.md`](map-and-tracking.md). Часть 1 оттуда (публикация карты) уже реализована иначе, чем там описано: карта живёт на уровне **дистанции** (`distances.map_url` + углы), а не отдельной сущностью `CompetitionMap` на этап; рендер — osmdroid (Android) и Leaflet (web), а не MapLibre.

---

## 1. Что делает фича

1. Участник, зарегистрированный на соревнование **с аккаунтом** (`orienteering_participants.user_id` = его userId), перед стартом нажимает в деталях события «Включить трекинг». Телефон пишет GPS и отправляет точки на сервер почти в реальном времени.
2. Любой пользователь, **включая гостей**, открывает в деталях события «Онлайн-треки», выбирает **дистанцию** и видит на геопривязанной карте этой дистанции маркеры и треки её участников.
3. После финиша тот же экран показывает сохранённые треки (архивный режим, без опроса).

## 2. Принятые решения

| Вопрос | Решение |
|---|---|
| Задержка показа (фейр-плей) | **Реальное время**, без задержки. Бегуну при этом **не показываем ни карту, ни его позицию** — только статус записи (иначе телефон = навигатор, нарушение правил) |
| Канал к зрителю | **Polling** раз в 3–5 с с серверным курсором `since`. WebSocket — будущий этап |
| Канал от бегуна | HTTP-батчи раз в ~10 с, буфер в Room, досылка при появлении сети |
| Остановка трекинга | Все три: вручную; сервером, когда у участника появился результат; по таймауту |
| Доступ к просмотру | Все, включая гостей. Бегун один раз даёт согласие на публикацию трека |
| Выбор карты | Сначала дистанция → только её участники на её карте |
| Разрывы в треке | Точки с интервалом > **30 с** не соединяются линией; маркер без данных > **60 с** — серый с подписью «нет данных N мин» |
| Геопривязка карты | По **трём углам** (TL, TR, BR) — дорабатываем mapper |
| Изоляция | Трекинг — **отдельный процесс**; его отказ не должен влиять на финиш/результаты/публикацию |

---

## 3. Нефункциональное требование №1: изоляция отказов

> Если трекинг сломался (баг, перегрузка, OOM, упала его БД), соревнование продолжается: принимается финиш, пишутся и публикуются результаты.

### 3.1. Уже существующие риски (закрываются этапом 0)

- **Нет пула соединений.** `Databases.kt` делал `Database.connect(url = ...)` — каждая транзакция открывала новое JDBC-соединение, число соединений ничем не ограничено. Всплеск нагрузки исчерпывает `max_connections` Postgres (100 по умолчанию) → падают все запросы, включая сохранение результатов.
- **Общий rate-limit на IP.** nginx `api_zone` = 60 r/m на IP на весь `/api/`. На соревновании много телефонов за одним IP (Wi-Fi, CGNAT оператора). Live-результаты на Android опрашивают 4 эндпоинта раз в 30 с = 8 r/m на зрителя → 7–8 зрителей в одной сети выбирают лимит, и **запросы организатора получают 503**.

### 3.2. Архитектура изоляции

```
                        nginx
      ┌───────────────────┼─────────────────────────────┐
  /api/* (api_zone)   live-чтения (live_zone)   /api/live-track/* (tracking_zone)
  /api/.../save/* (write_zone)                   короткие таймауты, свой upstream
      │                   │                              │
      └──── app (APP_MODE=main) ─────┘            tracking (APP_MODE=tracking)
             пул Hikari ≤ 10                       пул Hikari ≤ 8, mem_limit, cpus
             │                                     │            │
      Postgres: БД competra  ◄── read-only роль ───┘            │
      (роль competra)            (CONNECTION LIMIT 3)           │
                                                   Postgres: БД competra_tracking
      app ──► RabbitMQ: result.saved ──► tracking  (роль competra_tracking, CONNECTION LIMIT 10)
             (fire-and-forget, вне транзакции)
```

Правила:
1. **Основной бэкенд никогда синхронно не зависит от трекинга.** Единственная связь main → tracking — асинхронное событие в RabbitMQ. Публикация — после коммита результата, в `try/catch`, с таймаутом, в отдельном scope; ошибка публикации только логируется.
2. **Tracking читает основную БД один раз на сессию** (при старте: проверка участника, имя, группа, дистанция) через read-only роль с `CONNECTION LIMIT` и `statement_timeout`. Дальше данные денормализованы в его собственную БД.
3. **Отдельный контейнер** из того же образа (`APP_MODE=tracking`), `mem_limit`/`cpus`, `restart: unless-stopped`. Упал → Docker поднимает, nginx на это время отдаёт 502 только на `/api/live-track/*`.
4. **В режиме tracking не запускаются** шедулеры основного приложения (`configureStatusScheduler`, `configureReminderNotificationScheduler`) — иначе дубли FCM-уведомлений и гонки статусов. И наоборот, main не монтирует роуты трекинга.
5. **Зрители обслуживаются из памяти** tracking-процесса, не из БД.
6. **Клиенты деградируют мягко:** бегун копит точки в Room и досылает с экспоненциальной паузой; зритель видит «онлайн-треки временно недоступны», остальной экран события работает.

### 3.3. Сценарии отказов

| Что сломалось | Финиш/результаты | Трекинг |
|---|---|---|
| Процесс tracking упал / OOM | Работают | Бегуны буферизуют; после рестарта досылают. Зрители видят заглушку |
| БД `competra_tracking` недоступна | Работают | `POST points` → 503, бегуны буферизуют |
| RabbitMQ недоступен | Работают (публикация — best effort) | Сессии закрываются по таймауту/вручную вместо «по результату» |
| Основная БД недоступна | Не работают (как и сейчас) | Новые сессии не стартуют; активные продолжают писать |
| Всплеск зрителей | Не затрагиваются (свои limit-зоны и процесс) | Деградация только tracking |

---

## 4. Этап 0 — защита основного бэкенда (делается сейчас, независимо от фичи)

1. **HikariCP** в `Databases.kt`: `maximumPoolSize` из `DB_POOL_SIZE` (по умолчанию 10), `connectionTimeout` 5 с (быстрый отказ вместо зависания), `poolName = competra-main`.
2. **nginx — раздельные limit-зоны:**
   - `live_zone` — публичные GET, которые опрашивают live-экраны (`participantGroups`, `participants/competition`, `results/competition`), высокий лимит, т.к. за одним IP много зрителей;
   - `write_zone` — `/api/event/orienteering/save/*`, записи организатора/судей не делят лимит с остальным трафиком того же IP;
   - `api_zone` — всё остальное, как было.
   Позже сюда же добавляется `tracking_zone` для `/api/live-track/`.

---

## 5. Геопривязка карты по трём точкам

### 5.1. Проблема
`mapper` `PrintWidget::showCompetraBounds` берёт 4 угла области печати и сводит их к bbox север-юг. Для карты, повёрнутой на магнитное склонение, растр растягивается в bbox и съезжает на десятки метров — GPS-трек не ляжет на тропы.

### 5.2. Модель
Добавляется пара полей к существующим: `distances.map_top_right_lat/lng`.

- `map_top_right_*` **заполнены** → `map_top_left_*` и `map_bottom_right_*` трактуются как **точные углы** растра. Четвёртый угол: `BL = TL + BR − TR`.
- `map_top_right_*` **пусты** → старая семантика bbox (север вверх), показываем как сейчас.

Перевод пикселя `(x, y)` растра `W×H` в WGS84: `P = TL + (x/W)·(TR − TL) + (y/H)·(BR − TR)`. Линейная интерполяция lat/lon на масштабе дистанции (единицы км) даёт погрешность много меньше GPS.

### 5.3. Изменения
- **mapper:** `showCompetraBounds` отдаёт 6 значений (`mapTopLeftLat/Lng`, `mapTopRightLat/Lng`, `mapBottomRightLat/Lng`) — это `p0`, `p1`, `p2` без сведения к bbox.
- **eSport:** колонки, `DistanceRequest`/`DistanceResponse`, `DistanceService`. При обновлении дистанции поля карты перезаписываются **только если в запросе есть `mapUrl`** — Android и формы редактирования дистанции карту не присылают, и раньше любое их сохранение стирало карту, прикреплённую через веб (открепления карты в клиентах нет).
- **web:** `AttachMapDialog` — 6 полей (+ разбор вставленного из mapper текста); `DistanceMapView` рисует растр по углам из `distanceMapCorners` (`lib/mapCorners.ts`).
- **Android:** `Distance.map: DistanceMap?` (domain, `corners()` достраивает 4-й угол / bbox), Room-колонки (миграция 50→51), `DistanceResponse`. В `DistanceRequest` карта не отправляется; локальный `updateDistance` подставляет карту из существующей записи. Рендер (этап 5) — osmdroid `GroundOverlay` с 4 углами, либо собственный `Overlay` с `Matrix.setPolyToPoly`, если `GroundOverlay` 6.1.20 даст артефакты.
- **web (сделано):** собственный `RotatedImageOverlay` (CSS-матрица по трём углам, без стороннего плагина — у `Leaflet.ImageOverlay.Rotated` лицензия Beerware и зависимость от глобального `L`).

---

## 6. Процесс tracking (eSport)

### 6.1. Развёртывание
- Тот же репозиторий и образ; `Application.module()` смотрит `APP_MODE` (`main` по умолчанию | `tracking`) и ставит соответствующий набор плагинов/роутов.
- Режим tracking: Serialization, Authentication (JWT — тот же `JWT_SECRET`), StatusPages, `/health`, `/health/ready`, роуты `/api/live-track/*`, свой пул к `competra_tracking`, read-only пул к `competra`, consumer RabbitMQ, фоновый «сторож» сессий.
- `docker-compose`: сервис `tracking`, `mem_limit: 512m`, `cpus: "1.0"`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. nginx upstream `tracking_backend`, `proxy_connect_timeout 2s`, `proxy_read_timeout 10s`.
- Postgres (тот же инстанс): БД `competra_tracking`, роль `competra_tracking` (`CONNECTION LIMIT 10`); роль `competra_tracking_ro` в БД `competra` — `SELECT` на `orienteering_participants`, `participant_groups`, `distances`, `competitions`, `orienteering_competitions`, `orienteering_results`; `CONNECTION LIMIT 3`, `statement_timeout = 3s`.

**Реализовано (этап 2):** `AppMode` + `Application.module()` → `trackingModule()` (`tracking/TrackingModule.kt`, `tracking/TrackingDatabases.kt`); `scripts/setup_tracking_db.sh` (идемпотентный, генерирует пароли в `.env`, вызывается CI после выкатки app и reload nginx); сервис `tracking` в compose; nginx `location /api/live-track/` с upstream через переменную и `resolver 127.0.0.11` — иначе недоступный контейнер tracking ронял бы `nginx -t`/старт nginx целиком; метка `service` в Loki.

Грабли для этапа 3:
- `newSuspendedTransaction` в Exposed при недоступном пуле Hikari падает **фатальной ошибкой корутин** («Fatal exception in coroutines machinery»), а не обычным исключением. Health-check поэтому идёт напрямую через JDBC (`HikariDataSource.connection.isValid`). Для рабочих запросов — `withContext(Dispatchers.IO) { transaction(db) { … } }` (как `HealthCheck.kt` основного приложения), не `newSuspendedTransaction`.
- `DatabaseConfig.defaultMaxAttempts = 1` для обеих БД трекинга: по умолчанию Exposed делает 3 попытки × `connectionTimeout`, и запрос висел ~9 с на каждую БД.
- Пулы с `initializationFailTimeout = -1`: процесс стартует и при недоступной БД, `/api/live-track/health` честно показывает DOWN (проверено: ответ ≤ 3 с, процесс жив).

### 6.2. Таблицы (`competra_tracking`)

**live_track_sessions**
| Колонка | Тип | Комментарий |
|---|---|---|
| `id` | UUID PK | |
| `competition_id` | VARCHAR(36) | |
| `distance_id` | BIGINT | индекс (`distance_id`, `status`) |
| `participant_id` | VARCHAR(200) | уникальность: одна `ACTIVE` сессия на участника |
| `user_id` | VARCHAR(200) | |
| `display_name`, `group_name` | VARCHAR | денормализация при старте |
| `start_number` | INT | |
| `status` | VARCHAR(20) | `ACTIVE` / `FINISHED` / `STOPPED` / `TIMED_OUT` |
| `close_reason` | VARCHAR(30) | `MANUAL` / `RESULT_SAVED` / `CONTROL_TIME` / `INACTIVITY` / `MAX_DURATION` |
| `started_at`, `closed_at`, `last_point_at` | BIGINT | Unix ms |
| `deadline_at` | BIGINT | старт + контрольное время + 60 мин; если КВ нет — старт + 6 ч |
| `last_batch_seq` | INT | идемпотентность батчей |
| `consent_at` | BIGINT | когда бегун дал согласие на публикацию |
| `track_encoded` | TEXT | заполняется при закрытии, формат `TrackCodec` |

**live_track_points** (только пока сессия активна)
| Колонка | Тип |
|---|---|
| `id` | BIGSERIAL PK — **он же курсор для зрителей** (порядок приёма, а не время точки) |
| `session_id` | UUID, индекс |
| `t` | BIGINT (время фикса, Unix ms) |
| `lat`, `lon` | DOUBLE |
| `accuracy_m` | REAL NULL |

При закрытии сессии точки сортируются по `t`, кодируются в `track_encoded`, строки удаляются.

Курсор = `id` приёма, а не `t`: бегун, потерявший связь, досылает точки со «старым» `t`; курсор по времени их бы потерял.

### 6.3. API (все ответы — `CommonModel<T>`, `status == 1`)

**Бегун (JWT):**

`POST /api/live-track/sessions` — старт или **возобновление** (если у участника уже есть `ACTIVE` сессия — возвращается она; нужно при перезапуске приложения).
```json
// request
{ "competitionId": "…", "participantId": "…", "consent": true }
// response.result
{ "sessionId": "…", "status": "ACTIVE", "lastBatchSeq": 0, "uploadIntervalSec": 10, "deadlineAt": 1790000000000 }
```
Проверки: `participant.user_id == jwt.userId`; соревнование сегодня (по дате старта соревнования, ±1 день на часовые пояса) и не завершено; у участника ещё нет результата; `consent == true`; у дистанции участника есть карта. Ошибки — 403/409/422 с понятным `BaseError`.

`POST /api/live-track/sessions/{id}/points`
```json
// request
{ "batchSeq": 17, "points": [ { "t": 1790000001000, "lat": 55.7512, "lon": 37.6184, "acc": 6.0 } ] }
// response.result
{ "status": "ACTIVE", "ackedBatchSeq": 17 }
// или, если сессия закрыта сервером:
{ "status": "FINISHED", "closeReason": "RESULT_SAVED", "ackedBatchSeq": 17 }
```
`batchSeq <= last_batch_seq` → no-op, ответ как при успехе (повтор после таймаута). Лимиты: ≤ 300 точек в батче, `|lat| ≤ 90`, `|lon| ≤ 180`, `t` в пределах [старт сессии − 5 мин; now + 1 мин]. Нарушения → точка отбрасывается, батч не отклоняется целиком.

`POST /api/live-track/sessions/{id}/stop` — ручная остановка (`STOPPED`, `MANUAL`).

**Зритель (публично):**

`GET /api/live-track/competitions/{competitionId}/distances` — дистанции, где есть/были треки:
```json
[ { "distanceId": 12, "name": "М21", "activeCount": 14, "totalCount": 37 } ]
```

`GET /api/live-track/distances/{distanceId}/live?since={cursor}`
```json
{
  "cursor": 90412,
  "serverTime": 1790000100000,
  "sessions": [
    { "sessionId": "…", "participantId": "…", "displayName": "Иванов Иван", "groupName": "М21",
      "startNumber": 101, "status": "ACTIVE", "lastPointAt": 1790000098000,
      "points": [[1790000090000, 55.75120, 37.61840], [1790000093000, 55.75131, 37.61852]] }
  ]
}
```
`since=0` → полные треки всех сессий дистанции (активных и закрытых сегодня). Далее — только точки с `id > since` и сессии со сменой статуса. `serverTime` нужен клиенту для «нет данных N мин» без доверия к часам телефона.

`GET /api/live-track/distances/{distanceId}/tracks` — архив: `[{ sessionId, participantId, displayName, groupName, startNumber, startedAt, trackEncoded }]`.

### 6.4. In-memory слой
- Приём батча: один multi-row `INSERT … RETURNING id` в `competra_tracking` → после коммита точки кладутся в память (`distanceId → sessionId → точки`) с их `id`.
- Чтение `live` — только из памяти. Холодный старт процесса: загрузка активных сессий и их точек из БД.
- Ограничение памяти: активные сессии текущего дня; закрытые выгружаются из памяти через 1 ч после закрытия (дальше — `tracks` из БД).

### 6.5. Закрытие сессий
- **Вручную** — `stop`.
- **По результату:** main публикует `result.saved`, tracking закрывает `ACTIVE` сессию участника (`FINISHED`, `RESULT_SAVED`). Клиент узнаёт из ответа на следующий `points`.
- **Сторож** (раз в минуту): `now > deadline_at` → `TIMED_OUT/CONTROL_TIME`; нет точек > 45 мин → `TIMED_OUT/INACTIVITY`.

### 6.6. Контракт RabbitMQ
- Exchange `competra.events` (topic, durable), routing key `result.saved`.
- Очередь `live-track.result-saved` (durable, DLX → существующий `dlx`/`dlq`).
- Payload: `{ "competitionId", "participantId", "resultStatus", "finishTime", "savedAt" }`.
- Публикует main из `OrienteeringResultService.upsert/upsertAll` **после** коммита; ошибка публикации — только лог.
- Consumer идемпотентен (закрытие уже закрытой сессии — no-op).

---

## 7. Android — бегун

- **Модуль `:core:tracking`** (как предлагалось в `map-and-tracking.md`): общий GPS-движок, вынесенный из `WorkoutTrackingService` (фильтр точности, `LocationManager.GPS_PROVIDER` — без Play Services из-за флейворов rustore/huawei), `CompetitionTrackingService` (foreground, `foregroundServiceType="location"`), Room-буфер.
- **Room:** `live_track_buffer(sessionId, t, lat, lon, acc, batchSeq NULL)` + `live_track_session(sessionId, competitionId, participantId, lastAckedBatchSeq, status)`.
- **Цикл отправки:** раз в `uploadIntervalSec` — собрать неотправленные точки в батч (фиксированный `batchSeq` присваивается до отправки, чтобы повтор был идемпотентным) → `POST points` → удалить подтверждённые. Ошибка → экспоненциальная пауза 10 с → 20 → 40 … ≤ 2 мин. Отдельный `OkHttpClient`, чтобы не занимать соединения основного клиента.
- **GPS:** фикс раз в 2 с / 3 м, отбрасывать `accuracy > 50 м`.
- **Остановка на клиенте:** кнопка «Стоп»; ответ сервера со статусом ≠ `ACTIVE`; локальный `deadlineAt`. После остановки — финальный флаш буфера.
- **UI:** кнопка в деталях события (видна: пользователь — участник с `userId`, соревнование сегодня, у дистанции есть карта). Экран трекинга без карты: «Запись идёт», время, отправлено/в очереди точек, состояние связи, «Стоп». Первый запуск — экран согласия на публикацию трека.
- **Разрешения:** `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_LOCATION` уже в манифесте. `ACCESS_BACKGROUND_LOCATION` не нужен — сервис стартует из UI. Просить отключить оптимизацию батареи (Huawei/Xiaomi).
- **Google Play:** foreground service типа `location` требует декларации в Play Console (с видео) — учесть при публикации.

## 8. Android — зритель

- `:core:eventdetails`, новый экран `live_tracks` в его навграфе. Кнопка «Онлайн-треки» в деталях события: соревнование сегодня **или** у него есть треки (`distances` непустой).
- Экран 1: список дистанций (`/distances`). Экран 2: карта дистанции — osmdroid, OSM-подложка + растр по 3 углам + КП (координаты из IOF XML) + треки.
- Опрос `live?since` раз в 5 с, пока экран на переднем плане и есть `ACTIVE` сессии; когда все закрыты — переход на `tracks`, опрос останавливается.
- Отрисовка: у каждого участника свой цвет; полилиния рвётся при разрыве > 30 с; маркер с номером; серый маркер + «нет данных N мин», если `serverTime − lastPointAt > 60 с`; фильтр по группам; чекбокс «хвост 5 мин / весь трек».
- Ошибка/5xx → плашка «онлайн-треки временно недоступны», пауза опроса 30 с.

## 9. Web — зритель

- Страница `/competitions/:id/live-tracks` (+ `/:distanceId`), кнопка на странице соревнования.
- Основа — `DistanceMapView` + `Leaflet.ImageOverlay.Rotated`, `Polyline`/`CircleMarker` как в `TrackMapView`.
- TanStack Query с `refetchInterval: 5000` и курсором в `queryKey`-независимом ref (накопление точек на клиенте).
- Архив через `TrackCodec.decode` (уже есть в `src/lib/trackCodec.ts`).

---

## 10. Оценка нагрузки

- 300 бегунов × 1 батч / 10 с = 30 `INSERT`/с по ~5 строк — тривиально для Postgres.
- 500 зрителей × 1 запрос / 5 с = 100 r/s из памяти — укладывается в один Ktor-процесс.
- Трафик бегуна: ~0,5 КБ / 10 с ≈ 200 КБ/ч.

## 11. Этапы

0. **HikariCP + раздельные limit-зоны nginx** (защита основного бэкенда).
1. **Три точки привязки:** mapper → eSport → web (`AttachMapDialog`, повёрнутый overlay) → Android (поля карты в модели дистанции).
2. **Каркас tracking:** `APP_MODE`, docker-compose, БД/роли, nginx upstream/location, health.
3. **API трекинга + `result.saved`** из main.
4. **Android: бегун** (`:core:tracking`).
5. **Android: зритель.**
6. **Web: зритель.**
7. Позже: плеер воспроизведения, сравнение участников, WebSocket, привязка трека к сплитам.

## 12. Открытые вопросы

- Источник OSM-тайлов для продакшна: `tile.openstreetmap.org` по usage policy не рассчитан на нагрузку приложений (касается и osmdroid, и Leaflet — уже сейчас).
- Эмпирика батареи на длинных дистанциях (2–3 ч) при фиксе раз в 2 с.
- Нужен ли организатору выключатель «запретить трекинг на этом соревновании».
