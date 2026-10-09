package app.margin.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File

@Database(
    entities = [
        FolderEntity::class, NoteEntity::class, TagEntity::class, NoteTagEntity::class, NoteLinkEntity::class,
        AttachmentEntity::class, RevisionEntity::class, TaskEntity::class, TemplateEntity::class,
        TemplateUsageEntity::class, SavedSearchEntity::class, NoteFtsEntity::class,
    ],
    version = MarginDatabase.VERSION,
    exportSchema = true,
)
abstract class MarginDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun folders(): FolderDao
    abstract fun tags(): TagDao
    abstract fun links(): LinkDao
    abstract fun attachments(): AttachmentDao
    abstract fun revisions(): RevisionDao
    abstract fun tasks(): TaskDao
    abstract fun templates(): TemplateDao
    abstract fun savedSearches(): SavedSearchDao
    abstract fun fts(): FtsDao

    companion object {
        const val VERSION = 1
        const val NAME = "margin.db"

        /**
         * Migrations, oldest first. Policy (docs/ARCHITECTURE.md §Migrations):
         *  - Every schema change adds an explicit Migration and a MigrationTest case.
         *  - Destructive fallback is never enabled: a failed migration stops the app from opening the
         *    database rather than silently deleting user data, and the pre-migration copy made by
         *    [backupBeforeMigration] remains available for recovery.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): MarginDatabase {
            backupBeforeMigration(context)
            return Room.databaseBuilder(context, MarginDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        /**
         * If an existing database is older than [VERSION], copy it (and its WAL) aside before Room
         * migrates it. Keeps the two most recent copies.
         */
        fun backupBeforeMigration(context: Context) {
            val db = context.getDatabasePath(NAME)
            if (!db.exists()) return
            val current = try {
                SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            } catch (e: Exception) {
                return // unreadable: let Room report the problem; never delete anything here
            }
            if (current <= 0 || current >= VERSION) return
            val dir = File(context.noBackupFilesDir, "db-backups").apply { mkdirs() }
            for (suffix in listOf("", "-wal", "-shm")) {
                val src = File(db.path + suffix)
                if (src.exists()) src.copyTo(File(dir, "pre-migration-v$current$suffix.db"), overwrite = true)
            }
            dir.listFiles()?.filter { it.name.startsWith("pre-migration-v") && it.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
        }
    }
}
