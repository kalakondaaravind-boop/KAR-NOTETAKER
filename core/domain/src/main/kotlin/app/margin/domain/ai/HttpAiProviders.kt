package app.margin.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI

/** Minimal HTTP abstraction so providers can be tested without a network. */
fun interface HttpTransport {
    /** POST JSON; returns (status, body). Throws IOException on network failure. */
    fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String>
}

/** java.net implementation (present on Android and the JVM). Never logs headers or bodies. */
object UrlConnectionTransport : HttpTransport {
    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.doOutput = true
            c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { s -> s.readNBytesCompat(4 * 1024 * 1024).toString(Charsets.UTF_8) }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) { val n = read(buf); if (n < 0) break; out.write(buf, 0, minOf(n, max - out.size())) }
        return out.toByteArray()
    }
}

/** Endpoint validation: HTTPS only, except loopback (for a local model server on the device). */
object Endpoints {
    fun validate(baseUrl: String): Boolean = try {
        val u = URI(baseUrl.trim())
        val host = u.host?.lowercase() ?: ""
        when (u.scheme?.lowercase()) {
            "https" -> host.isNotEmpty() && u.userInfo == null
            "http" -> host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"
            else -> false
        }
    } catch (e: Exception) { false }
}

data class HttpProviderConfig(
    val baseUrl: String,
    /** User-supplied; stored encrypted on device by the app layer, never in source or the APK. */
    val apiKey: String,
    val model: String,
    val timeoutMs: Int = 60_000,
) {
    /**
     * OpenAI's own API: current (reasoning) models reject `max_tokens` and need `max_completion_tokens`,
     * and spend part of that budget on hidden reasoning, so the budget is raised to avoid empty answers.
     * Other OpenAI-compatible servers (e.g. llama.cpp) keep the classic `max_tokens`.
     */
    val isOfficialOpenAi: Boolean get() = try { URI(baseUrl.trim()).host?.lowercase() == "api.openai.com" } catch (e: Exception) { false }
}

/** OpenAI defaults used by the app's hidden key setup. The model can be changed there or in Settings → AI. */
object OpenAiDefaults {
    const val BASE_URL = "https://api.openai.com/v1"
    const val MODEL = "gpt-5.4-mini"
    /** Minimum completion budget for reasoning models (reasoning tokens + visible answer). */
    const val MIN_COMPLETION_TOKENS = 4096
}

/** Pulls `error.message` out of an OpenAI-style error body so the user sees e.g. "model not found", never the key. */
internal fun errorMessage(body: String): String? = try {
    ((Json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject)?.get("message")
        ?.let { (it as? JsonPrimitive)?.contentOrNull }?.take(200)
} catch (e: Exception) { null }

private abstract class HttpProviderBase(
    protected val config: HttpProviderConfig,
    protected val transport: HttpTransport,
) : AiProvider {
    override val sendsDataOffDevice: Boolean get() = !config.baseUrl.contains("://localhost") && !config.baseUrl.contains("://127.0.0.1")
    override fun supports(action: AiAction) = action != AiAction.FIND_RELATED && action != AiAction.FIND_DUPLICATES

    protected abstract fun url(): String
    protected abstract fun headers(): Map<String, String>
    protected abstract fun body(p: Prompt): String
    protected abstract fun extract(json: JsonObject): String?

    override suspend fun run(request: AiRequest, prompt: Prompt): AiResult = withContext(Dispatchers.IO) {
        if (!Endpoints.validate(config.baseUrl)) return@withContext AiResult.Failure(AiError.NOT_CONFIGURED, "Endpoint must use HTTPS")
        if (config.apiKey.isBlank() && sendsDataOffDevice) return@withContext AiResult.Failure(AiError.NOT_CONFIGURED, "API key missing")
        if (config.model.isBlank()) return@withContext AiResult.Failure(AiError.NOT_CONFIGURED, "Model missing")
        val (code, text) = try {
            transport.post(url(), headers(), body(prompt), config.timeoutMs)
        } catch (e: SocketTimeoutException) {
            return@withContext AiResult.Failure(AiError.TIMEOUT)
        } catch (e: IOException) {
            return@withContext AiResult.Failure(AiError.NETWORK)
        }
        when (code) {
            in 200..299 -> {
                val out = try { extract(Json.parseToJsonElement(text) as JsonObject) } catch (e: Exception) { null }
                if (out.isNullOrBlank()) AiResult.Failure(AiError.BAD_RESPONSE) else AiResult.Success(out.trim(), displayName, request.sources, local = false)
            }
            401, 403 -> AiResult.Failure(AiError.AUTH, errorMessage(text))
            413 -> AiResult.Failure(AiError.INPUT_TOO_LARGE)
            429 -> AiResult.Failure(AiError.RATE_LIMITED, errorMessage(text))
            else -> AiResult.Failure(AiError.SERVER, "HTTP $code" + (errorMessage(text)?.let { ": $it" } ?: ""))
        }
    }
}

/** Any OpenAI-compatible Chat Completions endpoint (cloud services or a local server such as llama.cpp). */
class OpenAiCompatibleProvider(config: HttpProviderConfig, transport: HttpTransport = UrlConnectionTransport) : AiProvider by Impl(config, transport) {
    private class Impl(config: HttpProviderConfig, transport: HttpTransport) : HttpProviderBase(config, transport) {
        override val id = "openai-compatible"
        override val displayName = "OpenAI-compatible (${config.model})"
        override fun url() = config.baseUrl.trimEnd('/') + "/chat/completions"
        override fun headers() = if (config.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${config.apiKey}")
        override fun body(p: Prompt) = Json.encodeToString(JsonElement.serializer(), buildJsonObject {
            put("model", config.model)
            if (config.isOfficialOpenAi) put("max_completion_tokens", maxOf(p.maxOutputTokens, OpenAiDefaults.MIN_COMPLETION_TOKENS))
            else put("max_tokens", p.maxOutputTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", p.system) })
                add(buildJsonObject { put("role", "user"); put("content", p.user) })
            })
        })
        override fun extract(json: JsonObject): String? =
            (((json["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject)
                ?.get("content")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }
}

/** Anthropic Messages API. */
class AnthropicProvider(config: HttpProviderConfig, transport: HttpTransport = UrlConnectionTransport) : AiProvider by Impl(config, transport) {
    private class Impl(config: HttpProviderConfig, transport: HttpTransport) : HttpProviderBase(config, transport) {
        override val id = "anthropic"
        override val displayName = "Anthropic (${config.model})"
        override fun url() = config.baseUrl.trimEnd('/') + "/v1/messages"
        override fun headers() = mapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01")
        override fun body(p: Prompt) = Json.encodeToString(JsonElement.serializer(), buildJsonObject {
            put("model", config.model)
            put("max_tokens", p.maxOutputTokens)
            put("system", p.system)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", p.user) }) })
        })
        override fun extract(json: JsonObject): String? =
            (json["content"] as? JsonArray)?.mapNotNull { b ->
                (b as? JsonObject)?.takeIf { (it["type"] as? JsonPrimitive)?.contentOrNull == "text" }?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
            }?.joinToString("")
    }
}
