package app.margin.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.margin.data.db.MarginDatabase
import app.margin.data.prefs.SettingsRepositoryImpl
import app.margin.data.repo.AttachmentFiles
import app.margin.data.repo.AttachmentRepositoryImpl
import app.margin.data.repo.FolderRepositoryImpl
import app.margin.data.repo.LibraryRepositoryImpl
import app.margin.data.repo.NoteRepositoryImpl
import app.margin.data.repo.ReminderScheduler
import app.margin.data.repo.SearchRepositoryImpl
import app.margin.data.repo.TagRepositoryImpl
import app.margin.data.repo.TaskRepositoryImpl
import app.margin.data.repo.TemplateRepositoryImpl
import app.margin.data.security.KeystoreSecretStore
import app.margin.domain.model.Clock
import app.margin.domain.repository.AttachmentRepository
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.LibraryRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.SearchRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.repository.TaskRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.settings.SecretStore
import app.margin.domain.settings.SettingsRepository
import app.margin.feature.tasks.AlarmReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.secretsStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.System

    /** Long-lived scope for work that must outlive a screen (e.g. final autosave flush). */
    @Provides @Singleton @AppScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton fun database(@ApplicationContext c: Context): MarginDatabase = MarginDatabase.build(c)

    @Provides @Singleton fun attachmentFiles(@ApplicationContext c: Context) = AttachmentFiles(File(c.filesDir, "attachments"))

    @Provides @Singleton fun settings(@ApplicationContext c: Context): SettingsRepository = SettingsRepositoryImpl(c.settingsStore)

    @Provides @Singleton fun secrets(@ApplicationContext c: Context): SecretStore = KeystoreSecretStore(c.secretsStore)

    @Provides @Singleton fun notes(db: MarginDatabase, clock: Clock, settings: SettingsRepository, files: AttachmentFiles): NoteRepository =
        NoteRepositoryImpl(db, clock, { settings.current().revisions }, files)

    @Provides @Singleton fun folders(db: MarginDatabase, clock: Clock): FolderRepository = FolderRepositoryImpl(db, clock)
    @Provides @Singleton fun tags(db: MarginDatabase): TagRepository = TagRepositoryImpl(db)

    @Provides @Singleton fun searchImpl(db: MarginDatabase, clock: Clock) = SearchRepositoryImpl(db, clock)
    @Provides @Singleton fun search(impl: SearchRepositoryImpl): SearchRepository = impl

    @Provides @Singleton fun scheduler(s: AlarmReminderScheduler): ReminderScheduler = s
    @Provides @Singleton fun tasks(db: MarginDatabase, clock: Clock, scheduler: ReminderScheduler): TaskRepository = TaskRepositoryImpl(db, clock, scheduler)
    @Provides @Singleton fun templates(db: MarginDatabase, clock: Clock): TemplateRepository = TemplateRepositoryImpl(db, clock)
    @Provides @Singleton fun attachments(db: MarginDatabase, files: AttachmentFiles, clock: Clock): AttachmentRepository = AttachmentRepositoryImpl(db, files, clock)
    @Provides @Singleton fun library(db: MarginDatabase, files: AttachmentFiles, clock: Clock): LibraryRepository = LibraryRepositoryImpl(db, files, clock)
}
