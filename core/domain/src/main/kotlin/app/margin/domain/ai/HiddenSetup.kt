package app.margin.domain.ai

/** Taps on the logo needed to open the hidden OpenAI setup, and the longest pause allowed between them. */
const val SECRET_TAP_COUNT = 10
const val SECRET_TAP_WINDOW_MS = 1_500L

/** Pure tap counter (unit-testable): returns true on the tap that completes the sequence, then starts over. */
class SecretTapCounter(private val needed: Int = SECRET_TAP_COUNT, private val windowMs: Long = SECRET_TAP_WINDOW_MS) {
    private var taps = 0
    private var last = Long.MIN_VALUE
    fun tap(nowMs: Long): Boolean {
        taps = if (last != Long.MIN_VALUE && nowMs - last <= windowMs) taps + 1 else 1
        last = nowMs
        if (taps >= needed) { taps = 0; last = Long.MIN_VALUE; return true }
        return false
    }
}

/** Plausibility only (the real check is OpenAI's reply): OpenAI keys start with "sk-" and are long. */
fun looksLikeOpenAiKey(k: String): Boolean = k.trim().let { it.startsWith("sk-") && it.length >= 20 && it.none(Char::isWhitespace) }
