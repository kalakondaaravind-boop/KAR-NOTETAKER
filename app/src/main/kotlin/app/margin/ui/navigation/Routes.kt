package app.margin.ui.navigation

import android.net.Uri

/** String routes (no serialization plugin needed). Arguments are URI-encoded. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val NOTES = "notes?collection={collection}"
    const val EDITOR = "editor/{noteId}?preview={preview}"
    const val SEARCH = "search?q={q}&folder={folder}"
    const val FOLDERS = "folders"
    const val TAGS = "tags"
    const val TEMPLATES = "templates?pickFor={pickFor}"
    const val TEMPLATE_FORM = "template/{templateId}"
    const val TEMPLATE_BUILDER = "template-builder?id={id}"
    const val TASKS = "tasks"
    const val ATTACHMENTS = "attachments"
    const val KNOWLEDGE = "knowledge"
    const val ASK = "ask"
    const val SETTINGS = "settings"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_DATA = "settings/data"
    const val REVISIONS = "revisions/{noteId}"
    const val DRAWING = "drawing/{noteId}?attachment={attachment}&page={page}"

    fun notes(c: Collection) = "notes?collection=${Uri.encode(c.encode())}"
    fun editor(noteId: String, preview: Boolean = false) = "editor/${Uri.encode(noteId)}?preview=$preview"
    fun search(q: String = "", folder: String? = null) = "search?q=${Uri.encode(q)}&folder=${Uri.encode(folder ?: "")}"
    fun templates(pickFor: String? = null) = "templates?pickFor=${Uri.encode(pickFor ?: "")}"
    fun templateForm(id: String) = "template/${Uri.encode(id)}"
    fun templateBuilder(id: String? = null) = "template-builder?id=${Uri.encode(id ?: "")}"
    fun revisions(noteId: String) = "revisions/${Uri.encode(noteId)}"
    fun drawing(noteId: String, attachment: String? = null, page: String? = null) =
        "drawing/${Uri.encode(noteId)}?attachment=${Uri.encode(attachment ?: "")}&page=${Uri.encode(page ?: "")}"
}

/** What a notes list shows. Encoded into the route as "kind:arg". */
sealed interface Collection {
    data object All : Collection
    data object Inbox : Collection
    data object Pinned : Collection
    data object Favorites : Collection
    data object Archive : Collection
    data object Trash : Collection
    data class InFolder(val folderId: String) : Collection
    data class WithTag(val tagId: String, val tagKey: String) : Collection
    data class Smart(val name: String) : Collection

    fun encode(): String = when (this) {
        All -> "all"; Inbox -> "inbox"; Pinned -> "pinned"; Favorites -> "favorites"; Archive -> "archive"; Trash -> "trash"
        is InFolder -> "folder:$folderId"
        is WithTag -> "tag:$tagId:$tagKey"
        is Smart -> "smart:$name"
    }

    companion object {
        fun decode(s: String?): Collection {
            val v = s.orEmpty()
            return when {
                v == "inbox" -> Inbox
                v == "pinned" -> Pinned
                v == "favorites" -> Favorites
                v == "archive" -> Archive
                v == "trash" -> Trash
                v.startsWith("folder:") -> InFolder(v.removePrefix("folder:"))
                v.startsWith("tag:") -> v.removePrefix("tag:").split(':', limit = 2).let { WithTag(it[0], it.getOrElse(1) { "" }) }
                v.startsWith("smart:") -> Smart(v.removePrefix("smart:"))
                else -> All
            }
        }
    }
}
