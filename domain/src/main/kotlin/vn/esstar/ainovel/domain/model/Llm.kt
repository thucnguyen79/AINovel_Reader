package vn.esstar.ainovel.domain.model

/** Where the user's own API key points. Only the user supplies keys; the app ships none. */
enum class LlmProviderKind { GEMINI, OPENROUTER, GROQ, OPENAI_COMPATIBLE }

/** BYOK settings for the "Dịch bằng AI" mode. */
data class LlmSettings(
    val provider: LlmProviderKind = LlmProviderKind.GEMINI,
    val apiKey: String = "",
    /** Empty = provider default from [defaultModel]. */
    val model: String = "",
    /** Only used by [LlmProviderKind.OPENAI_COMPATIBLE]; the others have fixed endpoints. */
    val baseUrl: String = "",
) {
    val effectiveModel: String get() = model.ifBlank { defaultModel(provider) }
    val effectiveBaseUrl: String get() = if (provider == LlmProviderKind.OPENAI_COMPATIBLE) baseUrl.trim().trimEnd('/') else defaultBaseUrl(provider)

    /** True when a request can be attempted: key present, model known, and a base URL for custom servers. */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && effectiveModel.isNotBlank() &&
            (provider != LlmProviderKind.OPENAI_COMPATIBLE || baseUrl.startsWith("http"))

    companion object {
        fun defaultModel(provider: LlmProviderKind): String = when (provider) {
            LlmProviderKind.GEMINI -> "gemini-2.5-flash"
            LlmProviderKind.OPENROUTER -> "meta-llama/llama-3.3-70b-instruct:free"
            LlmProviderKind.GROQ -> "llama-3.3-70b-versatile"
            LlmProviderKind.OPENAI_COMPATIBLE -> ""
        }

        fun defaultBaseUrl(provider: LlmProviderKind): String = when (provider) {
            LlmProviderKind.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
            LlmProviderKind.OPENROUTER -> "https://openrouter.ai/api/v1"
            LlmProviderKind.GROQ -> "https://api.groq.com/openai/v1"
            LlmProviderKind.OPENAI_COMPATIBLE -> ""
        }

        /** Models worth suggesting in the settings UI; the user can type any other name. */
        fun suggestedModels(provider: LlmProviderKind): List<String> = when (provider) {
            LlmProviderKind.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
            LlmProviderKind.OPENROUTER -> listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemma-3-27b-it:free",
                "qwen/qwen3-235b-a22b:free",
                "deepseek/deepseek-chat-v3-0324:free",
            )
            LlmProviderKind.GROQ -> listOf("llama-3.3-70b-versatile", "qwen/qwen3-32b", "llama-3.1-8b-instant")
            LlmProviderKind.OPENAI_COMPATIBLE -> emptyList()
        }
    }
}

/** A chapter rewritten by the model: title plus paragraphs aligned 1:1 with the source paragraphs. */
data class LlmChapter(
    val title: String,
    val paragraphs: List<String>,
    val provider: LlmProviderKind,
    val model: String,
)

/** Progress of a running chapter translation: [done] of [total] text chunks sent to the model. */
data class LlmProgress(val chapterId: Long, val done: Int, val total: Int)

sealed interface LlmTestResult {
    /** [detail] is a short human-readable note (model name, sample reply). */
    data class Success(val detail: String) : LlmTestResult
    data class Failure(val message: String) : LlmTestResult
}

data class LlmCacheInfo(val chapters: Int, val bytes: Long)

/** Raised by the client for HTTP or protocol errors; [userMessage] is safe to show as-is. */
class LlmException(val userMessage: String, cause: Throwable? = null) : RuntimeException(userMessage, cause)
