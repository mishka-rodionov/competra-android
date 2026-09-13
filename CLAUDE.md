# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

The app has a `store` flavor dimension with three flavors — `gplay`, `rustore`, `huawei` — so build tasks are per-flavor (e.g. `assembleGplayDebug`, `assembleRustoreRelease`). Bare `assembleDebug` builds all flavors.

```bash
./gradlew assembleGplayDebug       # Build debug APK for a store (gplay/rustore/huawei)
./gradlew assembleGplayRelease     # Build release APK for a store
./gradlew assembleDebug            # Build debug APK for ALL flavors
./gradlew build                    # Full build (all modules, all flavors)
./gradlew clean build              # Clean + full build

./gradlew test                     # Unit tests (all modules)
./gradlew :feature:center:test     # Unit tests for a specific module
./gradlew connectedAndroidTest     # Instrumented tests (requires device/emulator)
./gradlew lint                     # Static analysis
```

`applicationId` is `com.competra` (debug builds get a `.debug` suffix). AppMetrica keys are read from `local.properties` (`APPMETRICA_API_KEY_DEBUG` / `APPMETRICA_API_KEY_RELEASE`) and are not committed.

## Architecture

**Clean Architecture + MVVM + Multi-module**. Layer dependency direction: `:feature` → `:domain` ← `:data`. Features never depend on each other.

### Module Map

| Module | Role |
|---|---|
| `:app` | Entry point, DI wiring, bottom navigation, NFC dispatch |
| `:domain` | Pure Kotlin: models, repository interfaces, use cases |
| `:data:local` | Room database (DAOs, entities, type converters) |
| `:data:remote` | Retrofit services, data sources, OkHttp interceptors |
| `:data:navigation` | Navigation state/contracts |
| `:feature:center` | Competition creation, participants, draw, chip distribution, results |
| `:feature:events` | Events/competitions list and detail views |
| `:feature:profile` | User profile |
| `:core:designsystem` | Material 3 theme, colors, typography |
| `:core:nfchelper` | NFC read/write abstraction (MifareClassic, MifareUltralight, ParticipantCard, MasterCard) |
| `:core:ui` | Shared composables |
| `:core:resources` | Strings, drawables |
| `:core:sync` | Offline-first sync: `SyncOrchestrator`, `ConflictResolver`, `NetworkAvailabilityObserver`, WorkManager `SyncCenterWorker` (см. `docs/skills/offline-first-sync.md`) |
| `:core:analytics` | Analytics wrapper (`AnalyticsTracker` / `AppMetricaAnalyticsTracker`, `NavTracking`) — AppMetrica + Firebase Analytics |
| `:core:eventdetails` | Event detail feature: details / live_results / results / participant_group screens, own nav graph |
| `:utils` | Kotlin extension functions |

### Feature Module Structure

Each feature module follows this pattern:
```
feature/<name>/
  data/
    State.kt          # UI state data class
    Action.kt         # User action sealed class
    Interactor*.kt    # Business logic helpers
  presentation/
    *Screen.kt        # Composable screens
    *ViewModel.kt     # ViewModel with StateFlow<State>
```

### Dependency Injection

Koin is used throughout. All modules are registered in `SportApp.onCreate()` in `:app`. Each module has its own `*Module.kt` file. When adding a new dependency, wire it via a Koin module — no manual instantiation.

### Navigation

Type-safe Compose Navigation. Each feature exposes a navigation graph extension function (e.g., `centerGraph(...)`, `eventsGraph(...)`). The `NavHost` in `:app` composes all graphs.

### State Management

Features use a unidirectional State/Action pattern:
- ViewModel exposes `StateFlow<State>`
- UI sends `Action` objects to the ViewModel
- ViewModel mutates state via `_state.update { ... }`

## Key Technologies

- **Kotlin 2.3 / JDK 17** — use `jvmToolchain(17)`
- **Jetpack Compose + Material 3** — all UI is Compose, no XML layouts
- **Koin 4.1** — DI
- **Room 2.8 + KSP** — local persistence; KSP processes annotations at build time
- **Retrofit 3 + OkHttp 5** — networking; Chucker interceptor available in debug builds
- **Coroutines + Flow/StateFlow** — all async work
- **WorkManager** — background sync (`:core:sync`, `SyncCenterWorker`)
- **DataStore + Security Crypto** — for sensitive key-value storage
- **AppMetrica + Firebase** — analytics (AppMetrica primary, via `:core:analytics`), plus Firebase Crashlytics / Analytics / Messaging

## Code Conventions (from AGENTS.md)

- Do not delete commented-out lines
- Add KDoc to public declarations
- Follow existing package/module boundaries — do not add Android dependencies to `:domain`
- Use `EncryptedSharedPreferences` or `SQLCipher` for sensitive data (never plain SharedPreferences for secrets)
- Prefer official Jetpack/AndroidX libraries; for images use Coil or Glide

## NFC

The app uses NFC to read/write participant chips for orienteering competitions. `core:nfchelper` abstracts tag types. NFC dispatch is handled in `MainActivity` (`onResume`/`onPause` reader mode, `onNewIntent` for tag discovery). The manifest declares `TECH_DISCOVERED` intent filter for NFC.

## SDK Targets

- `minSdk 26` / `targetSdk 34` / `compileSdk 36`
- Distributed to three stores via flavors: `gplay` (Google Play), `rustore` (RuStore), `huawei` (AppGallery)
- Physical NFC device required for NFC features; emulator cannot emulate NFC hardware

## Workflows и skills

Дополнительная документация для Claude лежит в `docs/workflows/` (императивные процессы — «когда X, делай Y») и `docs/skills/` (справочники / конвенции). Сами файлы в контекст автоматически НЕ загружаются — читай нужный по Read, когда сработал триггер ниже.

### Триггеры

- **Создаёшь новый экран** (новый `*Screen.kt` или новая запись в navigation-graph) → прочитай `docs/workflows/analytics.md` ДО написания кода.
- **Добавляешь новое пользовательское действие или фичу** (новый `Action`, новая кнопка, новый бизнес-сценарий) → прочитай `docs/workflows/analytics.md` и спроси пользователя про аналитику ДО написания кода.
- **Пишешь имя нового аналитического события или экрана** → сверься с `docs/skills/analytics-events.md`.

Индексы доступных файлов: [`docs/workflows/README.md`](docs/workflows/README.md), [`docs/skills/README.md`](docs/skills/README.md).

## Commands
- Always use rtk for commands (rtk grep, rtk find, rtk git, and etc.)

## Межпроектные связи

Этот проект — часть экосистемы из четырёх репозиториев:

| Проект | Путь | Роль |
|---|---|---|
| **eSport** (backend) | `/Users/rodionov/backend_projects/eSport` | Ktor-сервер, источник всего API |
| **competra-web-ts** | `/Users/rodionov/web_projects/competra-web-ts` | Веб-версия приложения (React + TypeScript + Vite), деплоится на `competra.ru`. Заменил собой прежний Kotlin/Wasm веб-клиент (`competra-web`, теперь архивный) |
| **mapper** | `/Users/rodionov/android_projects/mapper` | Qt/C++ редактор карт, создаёт дистанции в формате IOF XML |

### Правила для Claude

**При изменении функционала** (новый экран, новый API-вызов, новая бизнес-логика) — **спроси пользователя**: нужно ли то же самое сделать в `competra-web-ts`? Обе платформы покрывают одну доменную область (соревнования, дистанции, результаты, профиль), и фичи часто должны быть на обеих.

**При изменении модели данных или API-вызова** — **спроси**: не сломает ли это бэкенд (eSport)? Контракт: все ответы — `CommonModel<T>` с `status == 1`, конфликты — HTTP 409 (server-wins).

**NFC-фичи** (`:core:nfchelper`, чтение/запись чипов участников) — уникальны для Android, в Web аналога нет, спрашивать не нужно.

### Цепочка IOF XML
Mapper экспортирует дистанции → пользователь загружает файл через `DistanceRepository.importFromXml` (`:data:remote`) → eSport парсит через `IOFXmlParser.kt`. Если меняется логика загрузки, уточни, не нужно ли обновить парсер в eSport или аналогичный upload в `competra-web-ts` (`src/api/distanceRepository.ts`).