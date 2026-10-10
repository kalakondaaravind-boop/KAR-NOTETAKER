package app.margin.feature.ai

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.ai.OpenAiDefaults
import app.margin.domain.ai.ProviderType
import app.margin.domain.ai.SecretTapCounter
import app.margin.domain.ai.looksLikeOpenAiKey
import app.margin.domain.settings.SecretStore
import app.margin.domain.settings.SettingsRepository
import app.margin.ui.components.BrandLockup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiKeyState(val keyStored: Boolean = false, val model: String = OpenAiDefaults.MODEL, val active: Boolean = false)

@HiltViewModel
class AiKeyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) : ViewModel() {
    val state: StateFlow<AiKeyState> = settings.settings.map {
        AiKeyState(it.aiKeyStored, it.aiModel.ifBlank { OpenAiDefaults.MODEL }, it.ai.enabled && it.ai.provider == ProviderType.OPENAI_COMPATIBLE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiKeyState())

    /**
     * Stores the key encrypted (Android Keystore) and switches AI to OpenAI. Saving here is the user's
     * explicit consent to send the text of AI actions they run to OpenAI. A blank key keeps the stored one.
     */
    fun save(key: String, model: String, done: () -> Unit) = viewModelScope.launch {
        val k = key.trim()
        if (k.isNotEmpty()) secrets.put(SECRET_AI_KEY, k)
        settings.update {
            it.copy(
                aiKeyStored = true,
                aiBaseUrl = OpenAiDefaults.BASE_URL,
                aiModel = model.trim().ifBlank { OpenAiDefaults.MODEL },
                ai = it.ai.copy(enabled = true, provider = ProviderType.OPENAI_COMPATIBLE, cloudConsent = true),
            )
        }
        done()
    }

    /** Forgets the key and falls back to the on-device assistant; nothing can be sent until set up again. */
    fun remove(done: () -> Unit) = viewModelScope.launch {
        secrets.remove(SECRET_AI_KEY)
        settings.update { it.copy(aiKeyStored = false, ai = it.ai.copy(provider = ProviderType.LOCAL, cloudConsent = false)) }
        done()
    }
}

/**
 * The KAR Group logo with the hidden OpenAI setup: tapping it [app.margin.domain.ai.SECRET_TAP_COUNT] (10) times in a row opens the key dialog.
 * The gesture is deliberately not exposed as a button to accessibility services, so it stays out of sight.
 */
@Composable
fun BrandLockupWithAiUnlock(modifier: Modifier = Modifier, markSize: Dp = 40.dp, centered: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val counter = remember { SecretTapCounter() }
    val onUnlock by rememberUpdatedState { haptics.performHapticFeedback(HapticFeedbackType.LongPress); open = true }
    BrandLockup(
        modifier.pointerInput(Unit) { detectTapGestures(onTap = { if (counter.tap(SystemClock.uptimeMillis())) onUnlock() }) },
        markSize = markSize,
        centered = centered,
    )
    if (open) AiKeyDialog(onDismiss = { open = false })
}

@Composable
private fun AiKeyDialog(onDismiss: () -> Unit, vm: AiKeyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var model by rememberSaveable(s.model) { mutableStateOf(s.model) }
    val keyOk = if (s.keyStored) key.isBlank() || looksLikeOpenAiKey(key) else looksLikeOpenAiKey(key)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_key_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (s.active) R.string.ai_key_status_on else R.string.ai_key_status_off), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ai_key_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(if (s.keyStored) R.string.ai_key_replace_field else R.string.ai_key_field)) },
                    placeholder = { Text("sk-…") },
                    isError = key.isNotBlank() && !keyOk,
                    supportingText = if (key.isNotBlank() && !keyOk) { { Text(stringResource(R.string.ai_key_invalid)) } } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    model, { model = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.ai_model)) },
                    supportingText = { Text(stringResource(R.string.ai_key_model_help, OpenAiDefaults.MODEL)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                )
                if (s.keyStored) TextButton(onClick = { vm.remove { key = ""; onDismiss() } }) {
                    Text(stringResource(R.string.ai_key_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = keyOk && (s.keyStored || key.isNotBlank()) && model.isNotBlank(),
                onClick = { vm.save(key, model) { key = ""; onDismiss() } }) { Text(stringResource(R.string.ai_key_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
