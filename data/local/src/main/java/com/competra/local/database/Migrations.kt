package com.competra.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция с версии 27 на 28.
 * Добавляет поле startIntervalSeconds в таблицу orienteering_competitions.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE orienteering_competitions ADD COLUMN startIntervalSeconds INTEGER"
        )
    }
}

/**
 * Миграция с версии 32 на 33.
 * Добавляет поле imageUrl в таблицу orienteering_competitions.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE orienteering_competitions ADD COLUMN imageUrl TEXT"
        )
    }
}

/**
 * Миграция с версии 35 на 36.
 * Переименовывает колонку photo → avatarUrl в таблице users.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE users_new (
                id TEXT NOT NULL,
                firstName TEXT NOT NULL,
                lastName TEXT NOT NULL,
                middleName TEXT,
                birthDate INTEGER NOT NULL,
                gender TEXT NOT NULL,
                avatarUrl TEXT NOT NULL DEFAULT '',
                phoneNumber TEXT,
                email TEXT NOT NULL,
                qualification TEXT NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("INSERT INTO users_new SELECT id, firstName, lastName, middleName, birthDate, gender, photo, phoneNumber, email, qualification FROM users")
        db.execSQL("DROP TABLE users")
        db.execSQL("ALTER TABLE users_new RENAME TO users")
    }
}

/**
 * Миграция с версии 34 на 35.
 * Добавляет поле isDrawConducted в таблицу orienteering_competitions.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE orienteering_competitions ADD COLUMN isDrawConducted INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * Миграция с версии 33 на 34.
 * Меняет тип participantId в orienteering_results с INTEGER на TEXT,
 * чтобы соответствовать String-типу PK в orienteering_participants.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS orienteering_results_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                competitionId INTEGER NOT NULL,
                groupId INTEGER NOT NULL,
                participantId TEXT NOT NULL,
                startTime INTEGER,
                finishTime INTEGER,
                totalTime INTEGER,
                rank INTEGER,
                status TEXT NOT NULL,
                penaltyTime INTEGER NOT NULL DEFAULT 0,
                splits TEXT,
                isEditable INTEGER NOT NULL DEFAULT 1,
                isEdited INTEGER NOT NULL DEFAULT 0,
                isSynced INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(competitionId) REFERENCES orienteering_competitions(localCompetitionId) ON DELETE CASCADE,
                FOREIGN KEY(participantId) REFERENCES orienteering_participants(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO orienteering_results_new
            SELECT id, competitionId, groupId, CAST(participantId AS TEXT),
                   startTime, finishTime, totalTime, rank, status,
                   penaltyTime, splits, isEditable, isEdited, isSynced
            FROM orienteering_results
        """.trimIndent())
        db.execSQL("DROP TABLE orienteering_results")
        db.execSQL("ALTER TABLE orienteering_results_new RENAME TO orienteering_results")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_orienteering_results_competitionId ON orienteering_results(competitionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_orienteering_results_participantId ON orienteering_results(participantId)")
    }
}

/**
 * Миграция с версии 36 на 37.
 * Доводит все синхронизируемые сущности до общего «sync trait»:
 * добавляет serverUpdatedAt и syncError всем, isDeleted/lastModified участникам и результатам,
 * remoteId результатам. Также добавляет индексы по isSynced для быстрой выборки несинхронизированных.
 *
 * Поле serverUpdatedAt в Competition (Embedded) и в orienteering_competitions добавляется
 * без префикса, т.к. Competition.kt не использует prefix в @Embedded.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Competition (@Embedded в orienteering_competitions): serverUpdatedAt + syncError.
        // syncError было в data class Competition давно, но миграция для него не существовала —
        // добиваем ALTER TABLE сейчас. Если поле уже есть (на новых установках через Room
        // generated schema) — ловим SQLException и продолжаем.
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN serverUpdatedAt INTEGER")
        runCatching {
            db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN syncError TEXT")
        }

        // participant_groups
        db.execSQL("ALTER TABLE participant_groups ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE participant_groups ADD COLUMN syncError TEXT")

        // distances
        db.execSQL("ALTER TABLE distances ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE distances ADD COLUMN syncError TEXT")

        // organizers
        db.execSQL("ALTER TABLE organizers ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE organizers ADD COLUMN syncError TEXT")

        // stages
        db.execSQL("ALTER TABLE stages ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE stages ADD COLUMN syncError TEXT")

        // orienteering_participants — добавляем sync-поля
        db.execSQL("ALTER TABLE orienteering_participants ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orienteering_participants ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orienteering_participants ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE orienteering_participants ADD COLUMN syncError TEXT")

        // orienteering_results — добавляем sync-поля + remoteId
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN remoteId TEXT")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN syncError TEXT")

        // Индексы под быструю выборку незасинканных
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_competitions_unsynced ON orienteering_competitions(isSynced)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_groups_unsynced ON participant_groups(isSynced)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_distances_unsynced ON distances(isSynced)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_participants_unsynced ON orienteering_participants(isSynced)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_results_unsynced ON orienteering_results(isSynced)")
    }
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE distances ADD COLUMN finishControlPoint INTEGER")
    }
}

/**
 * Миграция с версии 38 на 39.
 * Добавляет колонку timeZoneId для соревнований — IANA-идентификатор часового пояса
 * (например, "Europe/Moscow"). Для существующих записей дефолт 'UTC', чтобы
 * текущая интерпретация (как-было до фичи) не менялась.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN timeZoneId TEXT NOT NULL DEFAULT 'UTC'")
    }
}

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN orient_server_updated_at INTEGER")
    }
}

val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN resultsUrl TEXT")
    }
}

/**
 * Миграция с версии 42 на 43.
 * Добавляет поле isTest соревнования (встроено через @Embedded Competition,
 * без префикса) в таблицу orienteering_competitions. Тестовые соревнования
 * исключаются из публичной ленты на сервере, локально хранятся как обычные.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN isTest INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Миграция с версии 44 на 45.
 * Добавляет таблицы тренировочного дневника: workouts (общие поля + sync-trait) и
 * специфичные для вида спорта детали (run_details/bike_details/ski_details, 1:1 по workoutId).
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workouts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId INTEGER,
                sportType TEXT NOT NULL,
                status TEXT NOT NULL,
                scheduledDate INTEGER,
                startedAt INTEGER,
                durationSeconds INTEGER,
                distanceMeters INTEGER,
                elevationGainMeters INTEGER,
                notes TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0,
                lastModified INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL DEFAULT 0,
                serverUpdatedAt INTEGER,
                syncError TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workouts_unsynced ON workouts(isSynced)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS run_details (
                workoutId INTEGER PRIMARY KEY NOT NULL,
                cadenceSpm INTEGER,
                FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_run_details_workoutId ON run_details(workoutId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bike_details (
                workoutId INTEGER PRIMARY KEY NOT NULL,
                cadenceRpm INTEGER,
                powerWatts INTEGER,
                FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bike_details_workoutId ON bike_details(workoutId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ski_details (
                workoutId INTEGER PRIMARY KEY NOT NULL,
                style TEXT NOT NULL,
                FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ski_details_workoutId ON ski_details(workoutId)")
    }
}

/**
 * Миграция с версии 45 на 46.
 * Добавляет колонку trackEncoded для live GPS-трекинга — закодированный одной строкой трек
 * тренировки (см. `TrackCodec` в `:domain`). Статус тренировки `IN_PROGRESS` (новое значение
 * enum'а WorkoutStatus) не требует изменений схемы — колонка status и так TEXT.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workouts ADD COLUMN trackEncoded TEXT")
    }
}

/**
 * Миграция с версии 46 на 47.
 * Добавляет поддержку формата "по выбору" (score-О): лимит времени, штраф за минуту опоздания
 * и порог сильного опоздания у группы участников; сумму баллов и штраф в очках у результата.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE participant_groups ADD COLUMN timeLimitMinutes INTEGER")
        db.execSQL("ALTER TABLE participant_groups ADD COLUMN scorePenaltyPerMinute INTEGER")
        db.execSQL("ALTER TABLE participant_groups ADD COLUMN maxLatenessMinutes INTEGER")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN totalScore INTEGER")
        db.execSQL("ALTER TABLE orienteering_results ADD COLUMN scorePenalty INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Миграция с версии 47 на 48.
 * Добавляет опциональный клуб-организатор соревнования (встроено через
 * @Embedded Competition, без префикса) в таблицу orienteering_competitions.
 */
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN organizingClubId TEXT")
    }
}

/**
 * Миграция с версии 48 на 49.
 * Добавляет номер стартового контрольного пункта (отдельная физическая старт-станция) —
 * используется при StartTimeMode.BY_START_STATION как источник реального времени старта участника,
 * по аналогии с уже существующим finishControlPoint.
 */
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE distances ADD COLUMN startControlPoint INTEGER")
    }
}

/**
 * Миграция с версии 49 на 50.
 * Контрольное время (КВ) для любого направления: КВ соревнования как умолчание для групп
 * (группа переопределяет его уже существующим timeLimitMinutes) и политика применения —
 * IGNORE / DISQUALIFY / SCORE_PENALTY.
 *
 * Существующим BY_CHOICE-стартам проставляем SCORE_PENALTY: их лимит времени со штрафом
 * очками работал и до появления политики.
 */
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE orienteering_competitions ADD COLUMN controlTimeMinutes INTEGER")
        db.execSQL(
            "ALTER TABLE orienteering_competitions ADD COLUMN overtimePolicy TEXT NOT NULL DEFAULT 'IGNORE'"
        )
        db.execSQL(
            "UPDATE orienteering_competitions SET overtimePolicy = 'SCORE_PENALTY' WHERE direction = 'BY_CHOICE'"
        )
    }
}
