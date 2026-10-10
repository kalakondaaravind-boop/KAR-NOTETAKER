package app.margin.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Speech-to-text through Android's speech recogniser (no microphone permission needed by the app:
 * the system recogniser records and returns text only).
 *
 * EXTRA_PREFER_OFFLINE asks the recogniser to transcribe on the tablet. Whether that happens depends on the
 * device having an offline speech pack for the language (Android Settings → System → Languages → Speech).
 */
object Dictation {
    fun intent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** Best transcript from a recogniser result, or null when cancelled / nothing was heard. */
    fun text(resultCode: Int, data: Intent?): String? =
        if (resultCode != Activity.RESULT_OK) null
        else data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Returns a function that starts dictation. [onText] receives the transcript; [onUnavailable] runs when the
 * tablet has no speech recogniser installed or enabled.
 */
@Composable
fun rememberDictation(prompt: String, onText: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val latestOnText by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        Dictation.text(res.resultCode, res.data)?.let { latestOnText(it) }
    }
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    return {
        try { launcher.launch(Dictation.intent(prompt)) } catch (e: ActivityNotFoundException) { latestUnavailable() }
    }
}
