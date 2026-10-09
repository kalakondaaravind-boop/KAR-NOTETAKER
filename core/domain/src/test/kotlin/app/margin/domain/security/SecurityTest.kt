package app.margin.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

class SecurityTest {
    private val now = Instant.parse("2025-01-01T12:00:00Z")
    private val on = LockSettings(enabled = true, timeout = Duration.ofMinutes(1))

    @Test fun lockPolicy() {
        assertFalse(LockPolicy.mustLock(LockSettings(), false, null, now))
        assertTrue(LockPolicy.mustLock(on, false, null, now)) // cold start
        assertFalse(LockPolicy.mustLock(on, true, null, now))
        assertFalse(LockPolicy.mustLock(on, true, now.minusSeconds(30), now))
        assertTrue(LockPolicy.mustLock(on, true, now.minusSeconds(60), now))
        assertTrue(LockPolicy.mustLock(on.copy(timeout = Duration.ZERO), true, now, now))
        assertTrue(LockPolicy.mustLock(on, true, now.plusSeconds(3600), now)) // clock moved back
        assertEquals(LockPolicy.Fallback.DEVICE_CREDENTIAL, LockPolicy.fallback(true))
        assertEquals(LockPolicy.Fallback.CANNOT_UNLOCK_SET_UP_SCREEN_LOCK, LockPolicy.fallback(false))
    }

    @Test fun notificationsHideContentByDefault() {
        assertEquals("You have a reminder", NotificationPrivacy.reminderText("Call the oncologist", showPreview = false))
        assertEquals("Call", NotificationPrivacy.reminderText("Call", showPreview = true))
    }

    @Test fun safeLogRejectsStrings() {
        assertEquals("save.failed attempts=3 storage=true", SafeLog.format(SafeLog.event("save.failed", "attempts" to 3, "storage" to true)))
        try { SafeLog.event("note.saved", "title" to "My diary"); fail() } catch (e: IllegalArgumentException) { /* ok */ }
        try { SafeLog.event("User typed: hello"); fail() } catch (e: IllegalArgumentException) { /* ok */ }
    }

    /** Security test: no API keys or secrets committed in source (scans the repository). */
    @Test fun noSecretsInSource() {
        var root = File(".").absoluteFile
        while (root.parentFile != null && !File(root, "settings.gradle.kts").exists()) root = root.parentFile
        if (!File(root, "settings.gradle.kts").exists()) return // not running inside the repo
        val patterns = listOf(
            Regex("""sk-[A-Za-z0-9]{20,}"""), Regex("""sk-ant-[A-Za-z0-9_-]{20,}"""), Regex("""AKIA[0-9A-Z]{16}"""),
            Regex("""AIza[0-9A-Za-z_-]{35}"""), Regex("""-----BEGIN (RSA |EC )?PRIVATE KEY-----"""), Regex("""ghp_[A-Za-z0-9]{36}"""),
        )
        val offenders = root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-offline", ".idea") }
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "properties", "json", "md", "toml", "txt", "tpl") }
            .filter { f -> f.readText().let { t -> patterns.any { it.containsMatchIn(t) } } }
            .map { it.relativeTo(root).path }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
