package app.margin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.margin.platform.MaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MarginApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createChannels()
        publishShortcuts()
        MaintenanceWorker.schedule(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_reminders_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURE, getString(R.string.channel_capture), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_capture_desc)
                setShowBadge(false)
            },
        )
    }

    /** Launcher shortcuts are dynamic so they always target this build's package (debug/release). */
    private fun publishShortcuts() {
        fun shortcut(id: String, action: String, label: Int, icon: Int) = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(getString(label))
            .setIcon(IconCompat.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action))
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
                shortcut("new_note", ACTION_NEW_NOTE, R.string.shortcut_new_note, R.drawable.ic_shortcut_note),
                shortcut("new_checklist", ACTION_NEW_CHECKLIST, R.string.shortcut_new_checklist, R.drawable.ic_shortcut_checklist),
                shortcut("search", ACTION_SEARCH, R.string.shortcut_search, R.drawable.ic_shortcut_search),
            ))
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_CAPTURE = "quick_capture"
        const val ACTION_NEW_NOTE = "app.margin.action.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "app.margin.action.NEW_CHECKLIST"
        const val ACTION_SEARCH = "app.margin.action.SEARCH"
        const val ACTION_OPEN_NOTE = "app.margin.action.OPEN_NOTE"
        const val ACTION_OPEN_TASKS = "app.margin.action.OPEN_TASKS"
        const val EXTRA_NOTE_ID = "note_id"
    }
}
