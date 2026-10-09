package app.margin.domain.security

import java.time.Duration
import java.time.Instant

/** App-lock rules. The lock never has a bypass: if authentication is unavailable the user must use device credentials. */
data class LockSettings(
    val enabled: Boolean = false,
    /** Zero = lock immediately when the app goes to the background. */
    val timeout: Duration = Duration.ofMinutes(1),
    /** Hide content in the recent-apps switcher and block screenshots (FLAG_SECURE). */
    val secureWindow: Boolean = true,
)

object LockPolicy {
    /**
     * Whether the UI must show the lock screen when returning to the foreground.
     * [backgroundedAt] null = the app has not been backgrounded since the last unlock (or this is a cold start).
     */
    fun mustLock(settings: LockSettings, unlockedThisProcess: Boolean, backgroundedAt: Instant?, now: Instant): Boolean {
        if (!settings.enabled) return false
        if (!unlockedThisProcess) return true // cold start / process death
        if (backgroundedAt == null) return false
        // A clock moved backwards must not extend an unlocked session.
        if (now.isBefore(backgroundedAt)) return true
        return Duration.between(backgroundedAt, now) >= settings.timeout
    }

    /** What to do when biometrics stop working (e.g. enrolment removed): fall back to device credential, never skip. */
    enum class Fallback { DEVICE_CREDENTIAL, CANNOT_UNLOCK_SET_UP_SCREEN_LOCK }

    fun fallback(deviceSecure: Boolean) = if (deviceSecure) Fallback.DEVICE_CREDENTIAL else Fallback.CANNOT_UNLOCK_SET_UP_SCREEN_LOCK
}

/** Notification text policy: content is hidden by default. */
object NotificationPrivacy {
    fun reminderText(title: String, showPreview: Boolean): String = if (showPreview) title.take(120) else "You have a reminder"
}

/**
 * Logging that cannot leak note content: only an event name and non-sensitive numeric/enum fields.
 * Strings are deliberately not accepted as field values.
 */
object SafeLog {
    data class Event(val name: String, val fields: Map<String, Any>)

    fun event(name: String, vararg fields: Pair<String, Any>): Event {
        require(Regex("""[a-z0-9_.]{1,64}""").matches(name)) { "event names are constants" }
        fields.forEach { (_, v) -> require(v is Number || v is Boolean || v is Enum<*>) { "only numbers, booleans and enums may be logged" } }
        return Event(name, fields.toMap())
    }

    fun format(e: Event) = e.name + e.fields.entries.joinToString("") { " ${it.key}=${it.value}" }
}
