package app.margin.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.feature.ai.BrandLockupWithAiUnlock
import app.margin.domain.ai.ProviderType
import app.margin.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** AI stays off unless the user explicitly picks the on-device option here; cloud setup only happens in Settings. */
    fun finish(localAi: Boolean, done: () -> Unit) = viewModelScope.launch {
        settings.update { it.copy(onboardingCompleted = true, ai = if (localAi) it.ai.copy(enabled = true, provider = ProviderType.LOCAL) else it.ai) }
        done()
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var localAi by rememberSaveable { mutableStateOf(false) }
    val pages = 4
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(progress = { (page + 1) / pages.toFloat() }, modifier = Modifier.fillMaxWidth())
            when (page) {
                0 -> {
                    BrandLockupWithAiUnlock(Modifier.fillMaxWidth().padding(vertical = 24.dp), markSize = 64.dp, centered = true)
                    Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
                }
                1 -> {
                    Page(Icons.Outlined.PhoneAndroid, stringResource(R.string.onb_private_title), stringResource(R.string.onb_private_body))
                }
                2 -> {
                    Page(Icons.Outlined.AutoAwesome, stringResource(R.string.onb_ai_title), stringResource(R.string.onb_ai_body))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!localAi, { localAi = false }, label = { Text(stringResource(R.string.onb_ai_off)) })
                        FilterChip(localAi, { localAi = true }, label = { Text(stringResource(R.string.onb_ai_local)) })
                    }
                    Text(stringResource(R.string.onb_ai_cloud_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Page(Icons.Outlined.Backup, stringResource(R.string.onb_backup_title), stringResource(R.string.onb_backup_body))
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) OutlinedButton(onClick = { page-- }) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                if (page < pages - 1) {
                    TextButton(onClick = { vm.finish(false, onFinished) }) { Text(stringResource(R.string.skip)) }
                    Button(onClick = { page++ }) { Text(stringResource(R.string.next)) }
                } else {
                    Button(onClick = { vm.finish(localAi, onFinished) }) { Text(stringResource(R.string.get_started)) }
                }
            }
        }
    }
}

@Composable
private fun Page(icon: ImageVector, title: String, body: String) {
    Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(body, style = MaterialTheme.typography.bodyLarge)
}
