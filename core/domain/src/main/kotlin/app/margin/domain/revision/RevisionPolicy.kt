package app.margin.domain.revision

import app.margin.domain.model.NoteRevision
import java.time.Duration
import java.time.Instant

/**
 * Revision history is local, per note, and separate from backups. A revision stores the
 * *previous* content whenever a save happens after a quiet interval or after a large change.
 */
data class RevisionSettings(
    val snapshotInterval: Duration = Duration.ofMinutes(10),
    /** A change of at least this fraction of the text also triggers a snapshot. */
    val largeChangeRatio: Double = 0.3,
    val maxRevisionsPerNote: Int = 50,
    val maxAge: Duration = Duration.ofDays(90),
    val enabled: Boolean = true,
)

object RevisionPolicy {

    fun shouldSnapshot(
        previousTitle: String, previousBody: String,
        newTitle: String, newBody: String,
        lastRevisionAt: Instant?, now: Instant, settings: RevisionSettings,
    ): Boolean {
        if (!settings.enabled) return false
        if (previousTitle == newTitle && previousBody == newBody) return false
        if (previousBody.isBlank() && previousTitle.isBlank()) return false // nothing worth keeping
        if (lastRevisionAt == null) return true
        if (Duration.between(lastRevisionAt, now) >= settings.snapshotInterval) return true
        val base = maxOf(previousBody.length, 1)
        return changeSize(previousBody, newBody) / base.toDouble() >= settings.largeChangeRatio
    }

    /** Cheap change estimate: characters outside the common prefix/suffix. */
    fun changeSize(a: String, b: String): Int {
        var p = 0
        val min = minOf(a.length, b.length)
        while (p < min && a[p] == b[p]) p++
        var s = 0
        while (s < min - p && a[a.length - 1 - s] == b[b.length - 1 - s]) s++
        return maxOf(a.length, b.length) - p - s
    }

    /**
     * Revisions to delete. Never touches pinned revisions, always keeps the newest one,
     * and never affects the current note (which is not a revision).
     */
    fun toPrune(revisions: List<NoteRevision>, now: Instant, settings: RevisionSettings): List<NoteRevision> {
        val sorted = revisions.sortedByDescending { it.createdAt }
        val unpinned = sorted.filterNot { it.pinned }
        val newestId = sorted.firstOrNull()?.id
        val prune = LinkedHashSet<NoteRevision>()
        unpinned.drop(settings.maxRevisionsPerNote).forEach { prune += it }
        unpinned.filter { Duration.between(it.createdAt, now) > settings.maxAge }.forEach { prune += it }
        return prune.filter { it.id != newestId }
    }
}
