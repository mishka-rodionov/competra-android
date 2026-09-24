package com.competra.domain.models.livetrack

/**
 * Накапливает треки зрителя из архива (`tracks`) и последовательных снимков `live`.
 *
 * Точки одной сессии объединяются без дублей (по времени фикса) и сортируются: досланные после
 * потери связи точки приходят позже, но «старше» уже показанных. При `reset` (сервер перезапущен)
 * точки сессий из снимка заменяются полными — остальные (из архива) остаются.
 */
class LiveTrackAccumulator {

    private val tracks = LinkedHashMap<String, ViewerTrack>()

    /** Курсор для следующего запроса `live` (`null` — ещё не было ответов). */
    var cursor: String? = null
        private set

    /** Время сервера из последнего снимка (Unix ms), 0 — снимков ещё не было. */
    var serverTime: Long = 0
        private set

    /** Архив: полные треки. У закрытых сессий архив полон и главнее накопленного. */
    fun applyArchive(archive: List<ViewerTrack>) {
        archive.forEach { archived ->
            val existing = tracks[archived.sessionId]
            tracks[archived.sessionId] = when {
                existing == null -> archived
                !archived.isActive -> archived.copy(points = merge(existing.points, archived.points))
                else -> existing.copy(points = merge(existing.points, archived.points))
            }
        }
    }

    /** Снимок `live`: метаданные всех сессий и новые точки. */
    fun applySnapshot(snapshot: LiveTrackSnapshot) {
        snapshot.sessions.forEach { update ->
            val existing = tracks[update.sessionId]
            val points = when {
                existing == null || snapshot.reset -> update.points.sortedBy { it.t }
                else -> merge(existing.points, update.points)
            }
            tracks[update.sessionId] = update.copy(points = points)
        }
        cursor = snapshot.cursor
        serverTime = snapshot.serverTime
    }

    /** Треки: сначала на дистанции, затем по стартовому номеру и имени. */
    fun tracks(): List<ViewerTrack> = tracks.values.sortedWith(
        compareByDescending<ViewerTrack> { it.isActive }
            .thenBy { it.startNumber ?: Int.MAX_VALUE }
            .thenBy { it.displayName }
    )

    /** Есть ли участники на дистанции. */
    fun hasActive(): Boolean = tracks.values.any { it.isActive }

    private fun merge(a: List<ViewerTrackPoint>, b: List<ViewerTrackPoint>): List<ViewerTrackPoint> =
        (a + b).distinctBy { it.t }.sortedBy { it.t }
}
