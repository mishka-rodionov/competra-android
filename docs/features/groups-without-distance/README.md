# Группы без дистанции

## Зачем

Регистрацию часто открывают раньше, чем спланированы дистанции. Теперь соревнование можно создать, добавить группы без дистанций и сразу открыть регистрацию: участники записываются в конкретную группу, а дистанции организатор назначает позже. Чтобы про них не забыли, группы без дистанции заметно помечены, а операции, которым дистанция нужна, без неё не выполняются.

## Как пользоваться

### Web

1. «Создать соревнование» → шаг «Дистанции»: его можно пропустить — кнопка «Далее» активна и без дистанций.
2. Шаг «Группы»: в диалоге группы и в «Стандартных группах» дистанция «Не назначена» (если дистанций нет совсем — поле не показывается, под ним подсказка). У такой группы в списке метка «без дистанции», ниже — предупреждение со списком групп.
3. После создания: «Управление» → соревнование. Пока у групп нет дистанции, под вкладками висит баннер «У групп … не назначена дистанция» с кнопкой «К группам».
4. Когда дистанции готовы — вкладка «Дистанции» (вручную или импорт IOF XML), затем «Группы» → «Изменить» → «Дистанция».
5. Участник на странице соревнования видит у такой группы «Дистанция уточняется»; регистрация работает как обычно.

### Android

1. Мастер создания → «Шаг 4: Дистанции»: «Далее» активна и без дистанций.
2. «Группы»: в диалоге группы первая карточка — «Не назначена»; в «Стандартных группах» дистанция по умолчанию «Не назначена». У группы в списке метка «без дистанции», над кнопками — предупреждение.
3. Экран управления соревнованием: пока соревнование не завершено, под названием — баннер со списком групп без дистанции.
4. «Жеребьёвка»: карточка «По дистанциям» неактивна, пока в группах с участниками нет дистанции, — вместо описания «Недоступно: не назначена дистанция у групп …». Общая и по группам работают.
5. «Считывание чипа»: сверху баннер со списком групп. Чип участника такой группы не считается — сообщение «Группе … не назначена дистанция — результат не посчитан. Назначьте дистанцию и прочитайте чип ещё раз»; ничего не сохраняется.

## Правила и граничные случаи

- **Публикация результатов.** Сервер отклоняет перевод `resultsStatus` в `PRELIMINARY`/`OFFICIAL` (HTTP 422, «Нельзя опубликовать результаты: у групп … не назначена дистанция»), если у группы без дистанции есть результаты с отметками чипа (`split_times`). Группы без отметок — импорт прошедших результатов, ручной ввод времени — не мешают: им дистанция не нужна.
- **Удалённая дистанция** считается неназначенной везде: у группы остаётся висячий `distanceId`. Android при загрузке с сервера превращает неизвестный id в «не назначена»; веб в диалоге «Изменить» показывает «Не назначена», но пока список дистанций не загружен, id не трогает.
- **Прошедшие соревнования** (`FINISHED`/`ARCHIVED`): баннеры не показываются — у импортированных протоколов групп без дистанций много, и это норма.
- **Старые версии Android** получают `distanceId: null` как `0`: поле у них `Long`, а Gson кладёт JSON `null` в примитив нулём. Крэша нет — группа выглядит как «без дистанции» (так же было и раньше с `0`).

## Для организатора

Регистрацию можно открыть, не дожидаясь дистанций. До старта назначьте дистанцию каждой группе с участниками — иначе не провести жеребьёвку по дистанциям, не прочитать чипы этих групп и не опубликовать результаты.

## Как устроено

**Модель.** На сервере `participant_groups.distance_id` nullable; `null` — дистанция не назначена. Раньше «не назначена» хранилась как `0` (Gson и `Long` в `ParticipantGroupRequest`): миграция при старте переводит `0 → NULL`, а `ParticipantGroupRequest.normalizedDistanceId()` нормализует `0` от старых клиентов. В Android локально — сентинел `NO_DISTANCE_ID = 0L` (Room-схема не менялась), на границе с сетью `0 ↔ null`.

**eSport** (`competra-server/eSport/`):
- `data/database/entity/ParticipantGroups.kt` — `distanceId` nullable; `data/database/Databases.kt` — `DROP NOT NULL` и `0 → NULL`.
- `data/requests/orienteering/ParticipantGroupRequest.kt`, `data/response/orienteering/ParticipantGroupResponse.kt` — `distanceId: Long?`.
- `data/services/OrienteeringCompetitionService.kt` — `isResultsPublication` (тест — `OrienteeringCompetitionServiceResultsPublishedTest`) и `groupsWithPunchesWithoutDistance` в `upsertInTransaction`.

**Web** (`competra-web-ts/`):
- `src/lib/groupDistance.ts` — `hasAssignedDistance`, `groupsWithoutDistance`.
- `src/features/management/NoDistanceNotice.tsx` — `NoDistanceBadge`, `GroupsWithoutDistanceBanner`.
- Мастер: `src/pages/CreateCompetitionPage.tsx` (шаг дистанций необязателен), `CreateCompetitionSteps.tsx`, `GroupDialog.tsx`, `StandardGroupsDialog.tsx`; `NO_DISTANCE_KEY` в `types.ts`.
- Управление: `src/pages/ManageCompetitionPage.tsx` (баннер), `ManageGroupsTab.tsx` (метка, название дистанции из списка дистанций), `AddGroupDialog.tsx`.
- Публичная страница: `src/features/competition-detail/GroupsTab.tsx` — «Дистанция уточняется».

**Android** (`competra-android/`):
- `domain/.../orienteering/GroupDistance.kt` — `NO_DISTANCE_ID`, `hasAssignedDistance`, `withoutDistance`.
- `data/remote` — мапперы и `ParticipantGroupPublishRequest`/`ParticipantGroupRequest`/`ParticipantGroupResponse` (`0 ↔ null`).
- `core/sync/SyncOrchestrator.kt` — группа без дистанции готова к выгрузке сразу, не ждёт `distance.remoteId`.
- `feature/center/.../OrienteeringCompetitionInteractor.kt` — неизвестный серверный `distanceId` → `NO_DISTANCE_ID`.
- `feature/center/.../presentation/common/NoDistanceNotice.kt` — `NoDistanceBadge`, `GroupsWithoutDistanceBanner`.
- Мастер: `CreateDistanceScreen.kt`, `ParticipantGroupCreator.kt`, `StandardGroupsDialog.kt`, `CreateParticipantGroupScreen.kt`.
- Блокировки: `presentation/draw/DrawViewModel.kt` + `DrawParticipantsScreen.kt`, `presentation/read_card/OrientReadCardViewModel.kt` + `OrientReadCardScreen.kt`; баннер — `presentation/event_control/orienteering/`.

## Ограничения и что не вошло

- Push-напоминание организатору перед стартом о группах без дистанции — отложено.
- Старт соревнования на Android без дистанций не блокируется — только баннер; блокируются операции, которым дистанция нужна (жеребьёвка по дистанциям, чтение чипа).
- Новых событий аналитики нет.

## История

- 26.10.2026 — группы без дистанции: необязательный шаг «Дистанции» в мастерах, метки и баннеры, блокировка жеребьёвки по дистанциям и чтения чипа на Android, отказ сервера публиковать результаты.
