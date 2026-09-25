# Skill: конвенции аналитических событий

Справочник по именованию и структуре событий в `AnalyticsEvent` / `AnalyticsScreen`. На этот файл ссылается [workflows/analytics.md](../workflows/analytics.md).

Все события идут в Яндекс AppMetrica через [`AppMetricaAnalyticsTracker`](../../core/analytics/src/main/java/com/competra/analytics/AppMetricaAnalyticsTracker.kt). Словарь событий — [`AnalyticsEvent.kt`](../../core/analytics/src/main/java/com/competra/analytics/AnalyticsEvent.kt), словарь экранов — [`AnalyticsScreen.kt`](../../core/analytics/src/main/java/com/competra/analytics/AnalyticsScreen.kt).

## Имена событий

- `snake_case`, латиница, без префикса проекта.
- Формат: `<domain>_<action>` или `<domain>_<entity>_<state>`.
  - `<domain>` — `auth`, `event`, `nfc`, `profile`, `create_competition`, `participant`, `results`, ...
  - `<action>` / `<state>` — `opened`, `clicked`, `submitted`, `viewed`, `applied`, `success`, `failed`, `attempted`, `started`, `finished`.
- Имя должно читаться как «что произошло», а не «что я нажал». `event_register_clicked` (что произошло: клик по регистрации), не `click_register_button` (что я нажал).

### Примеры из текущего кода

| Имя | Когда | Файл |
|---|---|---|
| `event_opened` | Открыта карточка соревнования | `EventsViewModel.kt` |
| `event_register_clicked` | Нажата кнопка «зарегистрироваться» | `EventDetailsViewModel.kt` |
| `auth_login_requested` | Запрошен код авторизации | `AuthViewModel.kt` |
| `auth_login_failed` | Авторизация не удалась | `AuthCodeViewModel.kt` |
| `nfc_chip_read_attempted` | Зафиксирована попытка чтения чипа | `OrientReadCardViewModel.kt` |
| `create_competition_step_completed` | Пользователь закончил один из шагов мастера | `OrienteeringCreatorViewModel.kt` |
| `participant_drawn` | Прошла жеребьёвка | `DrawViewModel.kt` |
| `participant_dns_marked` | Участник отмечен «Не стартовал» | `ParticipantListViewModel.kt` |

### Анти-паттерны

- ❌ `OnRegisterClick`, `RegisterClick` — это имя `Action`, не события. Имя события — `<domain>_<action>` в `snake_case`.
- ❌ `clickRegister`, `screenEvents` — camelCase.
- ❌ `competra_event_opened` — префикс проекта избыточен.
- ❌ `event_opened_v2` — версии не в имени; меняется набор параметров → новое имя или просто дополнение к параметрам.

## Имена экранов

- `snake_case`, формат `<area>_<screen>`: `events_list`, `event_details`, `profile_home`, `auth_code`, `nfc_read_card`, `create_competition_common`.
- Имя задаётся в `AnalyticsScreen.screenName` (см. [`AnalyticsScreen.kt`](../../core/analytics/src/main/java/com/competra/analytics/AnalyticsScreen.kt)).
- `<area>` совпадает с feature-модулем или функциональной зоной: `events`, `event`, `center`, `profile`, `auth`, `nfc`, `participant`.

## Параметры событий

- Ключи — `snake_case`.
- **Категориальные значения** — через enum внутри `AnalyticsEvent`, не строковые литералы. Это даёт компайл-тайм проверку и единый список значений.
  - Образцы: `AnalyticsEvent.EventSource`, `AuthFailureReason`, `NfcCardType`, `CreateCompetitionStep`, `ParticipantAddMethod`.
  - В `params` enum конвертируется в строку через `.name.lowercase()`.
- **Числовые ID** — `event_id`, `competition_id`, `participant_id`, тип `Long`.
- **Счётчики** — `participants_count`, `kinds_count` (тип `Int`).
- **Boolean-флаги** — формат `has_<something>` или `is_<something>`: `has_date_from`, `is_authorized`.
- **Free-form строка** допустима только когда категорий слишком много (например, `reason: String` в `NfcChipReadFailed`). Если категорий конечно много — делать enum.

## Что НЕЛЬЗЯ передавать в параметрах

- **PII в открытом виде:**
  - email → только домен (`email_domain`), см. `AnalyticsEvent.AuthLoginRequested`.
  - телефон, ФИО, дата рождения — не передавать.
  - точные GPS-координаты пользователя.
- **Секреты:** токены, ключи, chip UID (это идентификатор оборудования участника — приватные данные).
- **Сообщения серверных ошибок:** могут содержать PII или внутренние детали API. Передавать категорию (`reason: invalid_code`), не текст.

User ID — отдельный канал через `AnalyticsTracker.setUserId(userId)`, не параметр события.

## Структура кейса `AnalyticsEvent`

`AnalyticsEvent` — `sealed class` с двумя аргументами в primary: `eventName: String` и `params: Map<String, Any?>`.

### Событие без параметров

```kotlin
data object AuthCodeSubmitted : AnalyticsEvent("auth_code_submitted")
```

### Событие с параметрами

```kotlin
class EventOpened(eventId: Long, source: EventSource) : AnalyticsEvent(
    "event_opened",
    mapOf("event_id" to eventId, "source" to source.name.lowercase()),
)
```

Правила:
- Если событие принимает доменные значения → используем enum, в `params` мапим в `.name.lowercase()`.
- Если параметров два и больше → `class` с явным конструктором; параметры в `mapOf` собираем вручную.
- Если параметров нет → `data object`.

### Когда параметров много / они меняются

Если хочется передать «всё подряд» (как в `EventFilterApplied(filters: Map<...>)`) — лучше принять `Map<String, Any?>` или собрать его в маленьком private-расширении. Не плодить 10 параметров в конструкторе.

## Чек-лист перед коммитом нового события

- [ ] Имя в `snake_case`, формат `<domain>_<action>`.
- [ ] Категориальные значения вынесены в enum.
- [ ] PII не уходит в параметры.
- [ ] Если событие про конкретную сущность (соревнование, участник) — есть параметр-ID.
- [ ] Если есть `state` (success/failed) — он отдельным значением или отдельным событием, а не в имени с suffix-ом версии.
