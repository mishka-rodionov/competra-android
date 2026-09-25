package com.competra.domain.sync

/**
 * План применения серверного снимка сущностей к локальной БД при загрузке с сервера (pull).
 *
 * Правила (offline-first, server-wins только через 409 при push):
 * - локальная запись синхронизирована и есть на сервере → обновить серверной версией ([toUpdate]);
 * - локальная запись с неотправленными изменениями (правка или пометка на удаление) → не трогать:
 *   её выгрузит push, а если на сервере она успела измениться — сервер ответит 409 и
 *   ConflictResolver применит серверную версию;
 * - серверной записи нет локально → вставить ([toInsert]);
 * - локальная синхронизированная запись, которая уже была на сервере, но пропала из снимка →
 *   удалена на сервере, удалить локально ([toDelete]);
 * - локальная запись, ещё не попавшая на сервер, → не трогать.
 *
 * @param L Тип локальной записи.
 * @param S Тип серверной записи.
 * @property toUpdate Пары «локальная запись → серверная версия» для обновления.
 * @property toInsert Серверные записи, которых нет локально.
 * @property toDelete Локальные записи, удалённые на сервере.
 */
data class ServerMergePlan<L, S>(
    val toUpdate: List<Pair<L, S>>,
    val toInsert: List<S>,
    val toDelete: List<L>
)

/**
 * Строит [ServerMergePlan] для списка локальных записей [local] и серверного снимка [server].
 *
 * @param localKey Ключ сопоставления локальной записи с серверной; null — сопоставить нельзя.
 * @param serverKey Ключ серверной записи.
 * @param isLocalSynced true, если у локальной записи нет неотправленных изменений.
 * @param isLocalOnServer true, если локальная запись уже была на сервере (есть remoteId) —
 *   только такие записи удаляются, когда пропадают из серверного снимка.
 */
fun <L, S, K : Any> planServerMerge(
    local: List<L>,
    server: List<S>,
    localKey: (L) -> K?,
    serverKey: (S) -> K,
    isLocalSynced: (L) -> Boolean,
    isLocalOnServer: (L) -> Boolean
): ServerMergePlan<L, S> {
    val localByKey = local.mapNotNull { l -> localKey(l)?.let { it to l } }.toMap()
    val serverKeys = server.map(serverKey).toSet()

    val toUpdate = mutableListOf<Pair<L, S>>()
    val toInsert = mutableListOf<S>()
    server.forEach { s ->
        val existing = localByKey[serverKey(s)]
        when {
            existing == null -> toInsert += s
            isLocalSynced(existing) -> toUpdate += existing to s
            else -> Unit // неотправленные локальные изменения — выгрузит push
        }
    }

    val toDelete = local.filter { l ->
        isLocalOnServer(l) && isLocalSynced(l) && localKey(l) !in serverKeys
    }

    return ServerMergePlan(toUpdate = toUpdate, toInsert = toInsert, toDelete = toDelete)
}
