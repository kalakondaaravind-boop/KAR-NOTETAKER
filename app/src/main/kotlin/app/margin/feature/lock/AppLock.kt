package app.margin.feature.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.margin.R
import app.margin.domain.model.Clock
import app.margin.domain.security.LockPolicy
import app.margin.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App lock state. Locks on cold start and after the configured background timeout. There is no
 * bypass: authentication uses biometrics or the device PIN/pattern/password (DEVICE_CREDENTIAL),
 * so losing biometric enrolment never locks the user out and never skips the lock.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var unlockedThisProcess = false
    private var backgroundedAt: Instant? = null
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { evaluate() }
    }

    private suspend fun evaluate() {
        val s = settings.current().lock
        _locked.value = LockPolicy.mustLock(s, unlockedThisProcess, backgroundedAt, clock.now())
    }

    override fun onStart(owner: LifecycleOwner) { scope.launch { evaluate() } }
    override fun onStop(owner: LifecycleOwner) { backgroundedAt = clock.now() }

    fun onAuthenticated() {
        unlockedThisProcess = true
        backgroundedAt = null
        _locked.value = false
    }

    /** Called when the user turns the lock on so it doesn't immediately lock them out mid-session. */
    fun markUnlockedNow() = onAuthenticated()

    companion object {
        /** WEAK|DEVICE_CREDENTIAL is the combination supported on every API level ≥ 26 (no crypto object is used). */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        fun canAuthenticate(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

fun showAuthPrompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_prompt_title))
        .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
        .setAllowedAuthenticators(AppLockManager.AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit, error: String?, onError: (String) -> Unit) {
    var available by remember { mutableStateOf(AppLockManager.canAuthenticate(activity)) }
    // Re-check after returning from system settings (the user may just have set up a screen lock).
    LifecycleResumeEffect(Unit) {
        available = AppLockManager.canAuthenticate(activity)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { if (available) showAuthPrompt(activity, onUnlocked, onError) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(8.dp))
            Text(
                if (available) stringResource(R.string.lock_body) else stringResource(R.string.lock_no_credential),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Spacer(Modifier.size(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Spacer(Modifier.size(24.dp))
            if (available) {
                Button(onClick = { showAuthPrompt(activity, onUnlocked, onError) }) { Text(stringResource(R.string.unlock)) }
            } else {
                // No bypass: the user sets up a device screen lock, then unlocks with it.
                Button(onClick = { activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text(stringResource(R.string.lock_open_security_settings))
                }
            }
        }
    }
}
