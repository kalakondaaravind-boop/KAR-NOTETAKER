package app.margin

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.domain.model.Note
import app.margin.domain.repository.NoteRepository
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.SettingsRepository
import app.margin.feature.lock.AppLockManager
import app.margin.feature.lock.LockScreen
import app.margin.ui.navigation.AppShell
import app.margin.ui.navigation.Routes
import app.margin.ui.theme.MarginTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
    private val notes: NoteRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _navigation = Channel<String>(Channel.BUFFERED)
    /** One-off navigation requests from intents (shortcuts, widget, notifications). */
    val navigation = _navigation.receiveAsFlow()

    fun handle(intent: Intent?) {
        when (intent?.action) {
            MarginApp.ACTION_NEW_NOTE -> newNote("")
            MarginApp.ACTION_NEW_CHECKLIST -> newNote("- [ ] ")
            MarginApp.ACTION_SEARCH -> _navigation.trySend(Routes.search())
            MarginApp.ACTION_OPEN_TASKS -> _navigation.trySend(Routes.TASKS)
            MarginApp.ACTION_OPEN_NOTE -> intent.getStringExtra(MarginApp.EXTRA_NOTE_ID)?.let { _navigation.trySend(Routes.editor(it)) }
        }
    }

    private fun newNote(body: String) = viewModelScope.launch {
        val s = settings.value
        val n: Note = notes.create(body = body, folderId = s?.defaultFolderId)
        _navigation.send(Routes.editor(n.id))
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var lock: AppLockManager
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lock.register()
        if (savedInstanceState == null) vm.handle(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings
            if (s == null) {
                // Settings load from local storage in milliseconds; keep the window background meanwhile.
                Box(Modifier.fillMaxSize())
                return@setContent
            }
            LaunchedEffect(s.lock.enabled, s.lock.secureWindow) {
                // Hide content from screenshots and the recent-apps preview when the lock is on.
                if (s.lock.enabled && s.lock.secureWindow) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LaunchedEffect(s.quickCaptureNotification) {
                app.margin.feature.capture.QuickCaptureNotification.sync(applicationContext, s.quickCaptureNotification)
            }
            MarginTheme(s.theme, s.dynamicColor, s.glossyStyle) {
                val locked by lock.locked.collectAsStateWithLifecycle()
                var authError by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (s.lock.enabled && locked) {
                        LockScreen(this@MainActivity, onUnlocked = { authError = null; lock.onAuthenticated() }, error = authError, onError = { authError = it })
                    } else {
                        AppShell(
                            startDestination = if (s.onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
                            settings = s,
                            navigationRequests = vm.navigation,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handle(intent)
    }
}
