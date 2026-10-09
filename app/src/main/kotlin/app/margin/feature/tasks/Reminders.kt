package app.margin.feature.tasks

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.margin.MainActivity
import app.margin.MarginApp
import app.margin.R
import app.margin.data.repo.ReminderScheduler
import app.margin.domain.model.Clock
import app.margin.domain.model.TaskItem
import app.margin.domain.repository.TaskRepository
import app.margin.domain.security.NotificationPrivacy
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.task.ReminderPlanner
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminder scheduling with AlarmManager:
 *  - Exact alarms (setExactAndAllowWhileIdle) when the user has granted "Alarms & reminders";
 *  - otherwise an inexact window (may be delayed by up to ~10 minutes by Doze). The UI tells the
 *    user which mode is active and links to the system setting.
 * Alarms do not survive reboot, time or time-zone changes, so RescheduleReceiver re-plans them.
 * Re-scheduling is idempotent: the PendingIntent is keyed by task id (FLAG_UPDATE_CURRENT).
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun pending(taskId: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, taskId.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_TASK_ID, taskId),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    @android.annotation.SuppressLint("ScheduleExactAlarm") // guarded by canScheduleExact() + SecurityException
    override suspend fun schedule(task: TaskItem): Boolean {
        val now = clock.now()
        if (!ReminderPlanner.shouldSchedule(task, now)) return false
        val at = ReminderPlanner.reminderAt(task)!!.toEpochMilli()
        val pi = pending(task.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        return try {
            if (canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
            true
        } catch (e: SecurityException) {
            false // permission revoked between check and call: report honestly
        }
    }

    override suspend fun cancel(taskId: String) {
        pending(taskId, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }

    companion object {
        const val ACTION_FIRE = "app.margin.reminder.FIRE"
        const val ACTION_SNOOZE = "app.margin.reminder.SNOOZE"
        const val ACTION_DONE = "app.margin.reminder.DONE"
        const val EXTRA_TASK_ID = "task_id"
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmReminderScheduler.EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    AlarmReminderScheduler.ACTION_FIRE -> notify(context, id)
                    AlarmReminderScheduler.ACTION_SNOOZE -> { tasks.snooze(id, 10); NotificationManagerCompat.from(context).cancel(id.hashCode()) }
                    AlarmReminderScheduler.ACTION_DONE -> { tasks.complete(id); NotificationManagerCompat.from(context).cancel(id.hashCode()) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked explicitly before notify()
    private suspend fun notify(context: Context, id: String) {
        val task = tasks.get(id) ?: return
        if (task.isCompleted) return
        // Content is hidden on the notification unless the user enabled previews.
        val preview = settings.current().notificationPreviews
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).setAction(MarginApp.ACTION_OPEN_TASKS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(a: String) = PendingIntent.getBroadcast(
            context, (id + a).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(a).putExtra(AlarmReminderScheduler.EXTRA_TASK_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, MarginApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(NotificationPrivacy.reminderText(task.title, preview))
            .setContentText(context.getString(R.string.reminder_tap_to_open))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, MarginApp.CHANNEL_REMINDERS).setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(context.getString(R.string.reminder_public)).build(),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.reminder_snooze_10), action(AlarmReminderScheduler.ACTION_SNOOZE))
            .addAction(0, context.getString(R.string.reminder_done), action(AlarmReminderScheduler.ACTION_DONE))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id.hashCode(), n)
    }
}

/** Re-plans every pending reminder after reboot, app update, clock or time-zone change. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var scheduler: AlarmReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                tasks.tasksWithReminders().forEach { t -> scheduler.cancel(t.id); scheduler.schedule(t) }
            } finally {
                pending.finish()
            }
        }
    }
}
