package app.margin.domain.organization

import app.margin.domain.model.Folder
import java.text.Normalizer
import java.util.Locale

/** Typed validation failures (mapped to localised strings in the UI layer). */
enum class NameError { EMPTY, TOO_LONG, INVALID_CHARACTERS }

object Tags {
    const val MAX_LENGTH = 64

    /**
     * Uniqueness key: Unicode-normalised, trimmed, internal whitespace collapsed, leading '#'
     * removed and case-folded. "Work", " work ", "#WORK" all map to the same tag.
     */
    fun key(name: String): String = clean(name).lowercase(Locale.ROOT)

    /** Display form: cleaned but case preserved. */
    fun clean(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC)
            .trim()
            .trimStart('#')
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*/\s*"""), "/")
            .trim('/')
            .trim()

    fun validate(name: String): NameError? {
        val c = clean(name)
        return when {
            c.isEmpty() -> NameError.EMPTY
            c.length > MAX_LENGTH -> NameError.TOO_LONG
            c.any { it == ',' || it == '\n' || it == '\r' || it == '\t' } -> NameError.INVALID_CHARACTERS
            else -> null
        }
    }

    /** Parses "a, b ,#c" into distinct cleaned names (first spelling wins). */
    fun parseList(input: String): List<String> =
        input.split(',', '\n').map { clean(it) }.filter { it.isNotEmpty() && validate(it) == null }
            .distinctBy { it.lowercase(Locale.ROOT) }

    /** Hierarchical tags use '/', e.g. "work/clients". A filter on a parent includes its children. */
    fun matchesHierarchy(filterKey: String, tagKey: String): Boolean =
        tagKey == filterKey || tagKey.startsWith("$filterKey/")

    /** Autocomplete: prefix matches first, then substring matches, excluding already-applied tags. */
    fun suggest(input: String, existing: List<String>, applied: Collection<String>, limit: Int = 8): List<String> {
        val k = key(input)
        val appliedKeys = applied.map { key(it) }.toSet()
        val candidates = existing.filter { key(it) !in appliedKeys }
        if (k.isEmpty()) return candidates.sortedBy { it.lowercase(Locale.ROOT) }.take(limit)
        val prefix = candidates.filter { key(it).startsWith(k) || key(it).split('/').any { p -> p.startsWith(k) } }
        val contains = candidates.filter { it !in prefix && key(it).contains(k) }
        return (prefix.sortedBy { it.length } + contains.sortedBy { it.length }).take(limit)
    }
}

/**
 * Organisation model (docs/ARCHITECTURE.md §Organisation):
 *  - Top-level folders are "notebooks"; folders nest to at most [MAX_DEPTH] levels.
 *  - A note has exactly one primary location (a folder or the Inbox) and any number of tags.
 */
object Folders {
    const val MAX_DEPTH = 4
    const val MAX_NAME = 80

    fun validateName(name: String): NameError? {
        val t = name.trim()
        return when {
            t.isEmpty() -> NameError.EMPTY
            t.length > MAX_NAME -> NameError.TOO_LONG
            t.any { it == '/' || it == '\n' || it == '\\' } -> NameError.INVALID_CHARACTERS
            else -> null
        }
    }

    fun depthOf(id: String, byId: Map<String, Folder>): Int {
        var d = 0
        var cur: String? = id
        val seen = HashSet<String>()
        while (cur != null) {
            if (!seen.add(cur)) return Int.MAX_VALUE // corrupt cycle: treat as infinitely deep
            d++
            cur = byId[cur]?.parentId
        }
        return d
    }

    fun descendants(id: String, all: Collection<Folder>): Set<String> {
        val children = all.groupBy { it.parentId }
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            children[cur].orEmpty().forEach { if (out.add(it.id)) stack.addLast(it.id) }
        }
        return out
    }

    /** Height of the subtree rooted at [id] (1 = no children). */
    fun subtreeHeight(id: String, all: Collection<Folder>): Int {
        val children = all.groupBy { it.parentId }
        fun h(f: String, guard: Int): Int = if (guard > 64) 64 else 1 + (children[f].orEmpty().maxOfOrNull { h(it.id, guard + 1) } ?: 0)
        return h(id, 0)
    }

    enum class MoveError { INTO_ITSELF, INTO_DESCENDANT, TOO_DEEP, NOT_FOUND }

    /** Validates moving folder [id] under [newParentId] (null = make it a notebook). */
    fun validateMove(id: String, newParentId: String?, all: Collection<Folder>): MoveError? {
        val byId = all.associateBy { it.id }
        if (id !in byId) return MoveError.NOT_FOUND
        if (newParentId == null) return if (subtreeHeight(id, all) > MAX_DEPTH) MoveError.TOO_DEEP else null
        if (newParentId !in byId) return MoveError.NOT_FOUND
        if (newParentId == id) return MoveError.INTO_ITSELF
        if (newParentId in descendants(id, all)) return MoveError.INTO_DESCENDANT
        return if (depthOf(newParentId, byId) + subtreeHeight(id, all) > MAX_DEPTH) MoveError.TOO_DEEP else null
    }

    fun canCreateChild(parentId: String?, all: Collection<Folder>): Boolean =
        parentId == null || depthOf(parentId, all.associateBy { it.id }) < MAX_DEPTH

    /** Sibling names must be unique (case-insensitive); returns a free name such as "Ideas (2)". */
    fun uniqueSiblingName(name: String, parentId: String?, all: Collection<Folder>, excludeId: String? = null): String {
        val taken = all.filter { it.parentId == parentId && it.id != excludeId }.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
        val base = name.trim()
        if (base.lowercase(Locale.ROOT) !in taken) return base
        var n = 2
        while ("$base ($n)".lowercase(Locale.ROOT) in taken) n++
        return "$base ($n)"
    }

    /** Breadcrumb path such as "Work / Clients / Acme". */
    fun path(id: String?, byId: Map<String, Folder>): List<Folder> {
        val out = ArrayList<Folder>()
        var cur = id
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur)) {
            val f = byId[cur] ?: break
            out += f
            cur = f.parentId
        }
        return out.reversed()
    }

    /** Flattened tree in display order with depth, for pickers and the drawer. */
    fun flatten(all: Collection<Folder>, includeArchived: Boolean = false): List<Pair<Folder, Int>> {
        val children = all.filter { includeArchived || !it.archived }.groupBy { it.parentId }
        val out = ArrayList<Pair<Folder, Int>>()
        fun visit(parent: String?, depth: Int) {
            children[parent].orEmpty()
                .sortedWith(compareByDescending<Folder> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.name.lowercase(Locale.ROOT) })
                .forEach { out += it to depth; if (depth < 64) visit(it.id, depth + 1) }
        }
        visit(null, 0)
        return out
    }

    /** What deleting a folder affects. Notes are never deleted outright: see [FolderDeletePolicy]. */
    data class DeletePlan(val folderIds: Set<String>, val policy: FolderDeletePolicy)

    fun deletePlan(id: String, all: Collection<Folder>, policy: FolderDeletePolicy) =
        DeletePlan(setOf(id) + descendants(id, all), policy)
}

enum class FolderDeletePolicy {
    /** Notes from the deleted folders move to the Inbox (default, nothing is lost). */
    MOVE_NOTES_TO_INBOX,
    /** Notes move to Trash (restorable until trash retention expires). */
    TRASH_NOTES,
}
