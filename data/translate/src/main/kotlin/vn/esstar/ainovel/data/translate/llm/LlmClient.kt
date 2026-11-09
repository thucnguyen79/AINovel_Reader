package vn.esstar.ainovel.data.translate.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import vn.esstar.ainovel.domain.model.LlmException
import vn.esstar.ainovel.domain.model.LlmProviderKind
import vn.esstar.ainovel.domain.model.LlmSettings
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One chat completion round trip against the user's provider. */
interface LlmClient {
    val settings: LlmSettings

    /** Returns the model's text reply for [system] + [user]. Retries rate limits; throws [LlmException] otherwise. */
    suspend fun complete(system: String, user: String, temperature: Double = 0.3): String

    /** Model ids offered to this key, or empty when the provider has no listing endpoint. */
    suspend fun listModels(): List<String>
}

/** Builds the right client for the provider; all providers except Gemini speak the OpenAI chat API. */
class LlmClientFactory(private val http: OkHttpClient, private val json: Json) {
    fun create(settings: LlmSettings): LlmClient = when (settings.provider) {
        LlmProviderKind.GEMINI -> GeminiClient(http, json, settings)
        LlmProviderKind.OPENROUTER, LlmProviderKind.GROQ, LlmProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(http, json, settings)
    }
}

internal abstract class BaseLlmClient(http: OkHttpClient, protected val json: Json, final override val settings: LlmSettings) : LlmClient {

    /** LLM replies for a 2 000-character chunk routinely take longer than the 30 s page-fetch timeout. */
    protected val client: OkHttpClient = http.newBuilder()
        .callTimeout(READ_TIMEOUT_S + 10, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    protected val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Executes [request], retrying 429 and 5xx with backoff, and returns the body as a parsed JSON object. */
    protected suspend fun send(request: Request): JsonObject {
        var attempt = 0
        while (true) {
            attempt++
            val response = runCatching { client.newCall(request).await() }
                .getOrElse { throw LlmException(networkMessage(it), it) }
            response.use { r ->
                val body = r.body.string()
                if (r.isSuccessful) {
                    return runCatching { json.parseToJsonElement(body).jsonObject }
                        .getOrElse { throw LlmException("Máy chủ trả về dữ liệu không phải JSON.", it) }
                }
                val retryable = r.code == 429 || r.code == 503 || r.code == 502 || r.code == 500
                if (retryable && attempt < MAX_ATTEMPTS) {
                    val wait = r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: (BACKOFF_MS * (1L shl (attempt - 1)))
                    Timber.w("LLM %s -> HTTP %d, retry %d after %d ms", request.url.host, r.code, attempt, wait)
                    delay(wait.coerceIn(500, 60_000))
                } else {
                    throw LlmException(httpMessage(r.code, body))
                }
            }
        }
    }

    protected fun httpMessage(code: Int, body: String): String {
        val detail = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            (obj["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                ?: (obj["error"] as? JsonPrimitive)?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.take(200)
        val keyProblem = detail?.contains("api key", ignoreCase = true) == true || detail?.contains("api_key", ignoreCase = true) == true
        val base = when {
            keyProblem -> "API key bị từ chối."
            code == 400 -> "Yêu cầu không hợp lệ (kiểm tra tên model)."
            code == 401 || code == 403 -> "API key bị từ chối."
            code == 404 -> "Không tìm thấy model hoặc đường dẫn API."
            code == 429 -> "Vượt hạn mức (rate limit) của nhà cung cấp, thử lại sau."
            code in 500..599 -> "Máy chủ của nhà cung cấp đang lỗi."
            else -> "Lỗi HTTP $code."
        }
        return if (detail.isNullOrBlank()) base else "$base $detail"
    }

    private fun networkMessage(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Không phân giải được tên máy chủ. Kiểm tra mạng hoặc URL."
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Hết thời gian chờ phản hồi từ máy chủ."
        else -> "Lỗi mạng: ${t.message ?: t::class.simpleName}"
    }

    protected companion object {
        const val READ_TIMEOUT_S = 120L
        const val MAX_ATTEMPTS = 4
        const val BACKOFF_MS = 2_000L
    }
}

/** Google AI Studio / Gemini API: `models/{model}:generateContent` with the key in a header. */
internal open class GeminiClient(
    http: OkHttpClient,
    json: Json,
    settings: LlmSettings,
    /** Overridable so tests can point the fixed Google endpoint at a local server. */
    private val base: String = settings.effectiveBaseUrl,
) : BaseLlmClient(http, json, settings) {

    override suspend fun complete(system: String, user: String, temperature: Double): String {
        val payload = buildJsonObject {
            putJsonObject("system_instruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", user) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
                // Translation needs no reasoning pass; Gemini 2.5 Flash thinks by default, which triples latency.
                if (settings.effectiveModel.contains("2.5-flash")) putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
            }
        }
        val request = Request.Builder()
            .url("$base/models/${settings.effectiveModel}:generateContent")
            .header("x-goog-api-key", settings.apiKey)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        val obj = send(request)
        val candidate = (obj["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?: throw LlmException(blockedMessage(obj))
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
        if (text.isBlank()) throw LlmException("Gemini trả về nội dung rỗng (${candidate["finishReason"]?.jsonPrimitive?.contentOrNull ?: "không rõ lý do"}).")
        return text
    }

    private fun blockedMessage(obj: JsonObject): String {
        val reason = obj["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        return if (reason != null) "Gemini từ chối nội dung này ($reason)." else "Gemini không trả về kết quả."
    }

    override suspend fun listModels(): List<String> {
        val request = Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", settings.apiKey).get().build()
        val obj = send(request)
        return (obj["models"] as? JsonArray).orEmpty().mapNotNull { m ->
            val o = m.jsonObject
            val methods = o["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            if ("generateContent" !in methods) return@mapNotNull null
            o["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
        }.sorted()
    }
}

/** OpenAI-style `/chat/completions` and `/models`, used by OpenRouter, Groq and any compatible server. */
internal class OpenAiCompatibleClient(http: OkHttpClient, json: Json, settings: LlmSettings) : BaseLlmClient(http, json, settings) {

    private val base = settings.effectiveBaseUrl

    override suspend fun complete(system: String, user: String, temperature: Double): String {
        val payload = buildJsonObject {
            put("model", settings.effectiveModel)
            put("temperature", temperature)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
        }
        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .apply { if (settings.provider == LlmProviderKind.OPENROUTER) header("X-Title", "AINovel Reader") }
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        val obj = send(request)
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?: throw LlmException(obj["error"]?.let { "Máy chủ báo lỗi: ${it.toString().take(200)}" } ?: "Máy chủ không trả về lựa chọn nào.")
        val content = choice["message"]?.jsonObject?.get("content")
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
            else -> ""
        }
        if (text.isBlank()) throw LlmException("Model trả về nội dung rỗng (${choice["finish_reason"]?.jsonPrimitive?.contentOrNull ?: "không rõ lý do"}).")
        return text
    }

    override suspend fun listModels(): List<String> {
        val request = Request.Builder().url("$base/models").header("Authorization", "Bearer ${settings.apiKey}").get().build()
        val obj = send(request)
        return (obj["data"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.sorted()
    }
}

/** Usage-based price hint OpenRouter attaches to models; unused for now but handy when listing free models. */
internal fun JsonObject.promptPrice(): Double? = this["pricing"]?.jsonObject?.get("prompt")?.jsonPrimitive?.doubleOrNull

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
