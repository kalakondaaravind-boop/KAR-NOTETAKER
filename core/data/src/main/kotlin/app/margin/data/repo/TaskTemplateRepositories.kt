package app.margin.data.repo

import androidx.room.withTransaction
import app.margin.data.db.MarginDatabase
import app.margin.data.db.TemplateEntity
import app.margin.data.db.TemplateUsageEntity
import app.margin.data.db.toDomain
import app.margin.data.db.toEntity
import app.margin.domain.model.Clock
import app.margin.domain.model.TaskItem
import app.margin.domain.repository.TaskRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.task.TaskRules
import app.margin.domain.template.BuiltInTemplates
import app.margin.domain.template.TemplateCatalog
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateJson
import app.margin.domain.template.TemplateValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration

/** Scheduling side effects are injected so the repository stays platform-agnostic and testable. */
interface ReminderScheduler {
    /** Returns true only if the platform accepted the schedule (never reports success optimistically). */
    suspend fun schedule(task: TaskItem): Boolean
    suspend fun cancel(taskId: String)
}

class TaskRepositoryImpl(
    private val db: MarginDatabase,
    private val clock: Clock,
    private val scheduler: ReminderScheduler,
) : TaskRepository {
    override fun observeOpenTasks(): Flow<List<TaskItem>> = db.tasks().observeOpen().map { l -> l.map { it.toDomain() }.sortedWith(TaskRules.ordering) }
    override fun observeCompletedTasks(limit: Int): Flow<List<TaskItem>> = db.tasks().observeCompleted(limit).map { l -> l.map { it.toDomain() } }
    override fun observeTasksForNote(noteId: String): Flow<List<TaskItem>> = db.tasks().observeForNote(noteId).map { l -> l.map { it.toDomain() } }
    override suspend fun get(id: String): TaskItem? = db.tasks().get(id)?.toDomain()

    override suspend fun upsert(task: TaskItem) {
        require(TaskRules.validateTitle(task.title)) { "invalid title" }
        db.tasks().upsert(task.copy(updatedAt = clock.now()).toEntity())
        reschedule(task)
    }

    private suspend fun reschedule(task: TaskItem) {
        scheduler.cancel(task.id)
        if (!task.isCompleted) scheduler.schedule(task)
    }

    override suspend fun complete(id: String): TaskItem? {
        val (done, next) = db.withTransaction {
            val t = db.tasks().get(id)?.toDomain() ?: return@withTransaction null to null
            val c = TaskRules.complete(t, clock.now())
            db.tasks().upsert(c.completed.toEntity())
            c.nextOccurrence?.let { db.tasks().upsert(it.toEntity()) }
            c.completed to c.nextOccurrence
        }
        done?.let { scheduler.cancel(it.id) }
        next?.let { scheduler.schedule(it) }
        return next
    }

    override suspend fun reopen(id: String) {
        val t = db.tasks().get(id)?.toDomain() ?: return
        val reopened = t.copy(completedAt = null, updatedAt = clock.now())
        db.tasks().upsert(reopened.toEntity())
        reschedule(reopened)
    }

    override suspend fun delete(id: String) {
        db.tasks().delete(id)
        scheduler.cancel(id)
    }

    override suspend fun snooze(id: String, minutes: Long) {
        val t = db.tasks().get(id)?.toDomain() ?: return
        val now = clock.now()
        val s = TaskRules.snooze(t, now.plus(Duration.ofMinutes(minutes.coerceIn(1, 60L * 24 * 30))), now)
        db.tasks().upsert(s.toEntity())
        reschedule(s)
    }

    override suspend fun tasksWithReminders(): List<TaskItem> = db.tasks().withReminders().map { it.toDomain() }
}

class TemplateRepositoryImpl(private val db: MarginDatabase, private val clock: Clock) : TemplateRepository {

    /** Built-ins merged with user templates/overrides. Stored JSON that no longer validates is skipped, never crashes. */
    override fun observeTemplates(): Flow<List<TemplateDefinition>> = db.templates().observeAll().map { rows ->
        val user = rows.mapNotNull { r -> TemplateJson.decode(r.json).getOrNull()?.takeIf { TemplateValidator.validateSchema(it).isEmpty() } }
        TemplateCatalog(BuiltInTemplates.all, user).effective()
    }

    override suspend fun get(id: String): TemplateDefinition? {
        db.templates().get(id)?.let { r -> TemplateJson.decode(r.json).getOrNull()?.let { return it.copy(builtIn = BuiltInTemplates.byId(id) != null) } }
        return BuiltInTemplates.byId(id)
    }

    override suspend fun saveUserTemplate(t: TemplateDefinition) {
        val errors = TemplateValidator.validateSchema(t)
        require(errors.isEmpty()) { "invalid template: ${errors.first().path} ${errors.first().message}" }
        db.templates().upsert(TemplateEntity(t.id, TemplateJson.encode(t), clock.now().toEpochMilli()))
    }

    override suspend fun deleteUserTemplate(id: String) = db.templates().delete(id)
    override suspend fun resetBuiltIn(id: String) { if (BuiltInTemplates.byId(id) != null) db.templates().delete(id) }
    override fun observeRecentlyUsed(): Flow<List<String>> = db.templates().observeRecent()
    override suspend fun markUsed(id: String) = db.templates().markUsed(TemplateUsageEntity(id, clock.now().toEpochMilli()))
}
