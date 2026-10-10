package app.margin.feature.capture

import android.Manifest
import android.app.PendingIntent
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

/**
 * Optional, user-enabled persistent notification with one-tap capture actions (Settings → Capture).
 * It shows no note content, so it is safe on the lock screen.
 */
object QuickCaptureNotification {
    private const val ID = 4201

    fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows or removes the notification to match the setting. Returns false if it should show but can't (no permission). */
    fun sync(context: Context, enabled: Boolean): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!enabled) { nm.cancel(ID); return true }
        if (!canPost(context)) return false
        fun open(action: String, requestCode: Int) = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, MarginApp.CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.quick_capture))
            .setContentText(context.getString(R.string.quick_capture_notification_text))
            .setContentIntent(open(MarginApp.ACTION_NEW_NOTE, 41))
            .addAction(0, context.getString(R.string.new_note), open(MarginApp.ACTION_NEW_NOTE, 41))
            .addAction(0, context.getString(R.string.shortcut_new_checklist), open(MarginApp.ACTION_NEW_CHECKLIST, 42))
            .addAction(0, context.getString(R.string.shortcut_search), open(MarginApp.ACTION_SEARCH, 43))
            .setOngoing(true).setSilent(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        return try { nm.notify(ID, n); true } catch (e: SecurityException) { false }
    }
}
