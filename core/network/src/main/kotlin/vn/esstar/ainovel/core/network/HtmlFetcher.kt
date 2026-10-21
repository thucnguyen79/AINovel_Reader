package vn.esstar.ainovel.core.network

import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class FetchedPage(
    val requestUrl: String,
    val finalUrl: String,
    val html: String,
    val statusCode: Int,
    val charset: String = "UTF-8",
)

class HttpException(val code: Int, val url: String) : IOException("HTTP $code for $url")

enum class FetchMode {
    HTTP, WEBVIEW;

    companion object {
        /** `http` (default), `webview`, or the legacy alias `webview-cf`. */
        fun parse(value: String?): FetchMode = when (value?.lowercase()) {
            "webview", "webview-cf" -> WEBVIEW
            else -> HTTP
        }
    }
}

enum class FetchMethod {
    GET, POST;

    companion object {
        fun parse(value: String?): FetchMethod = if (value.equals("POST", ignoreCase = true)) POST else GET
    }
}

data class FetchOptions(
    val headers: Map<String, String> = emptyMap(),
    val charsetHint: String? = null,
    val mode: FetchMode = FetchMode.HTTP,
    /** CSS selector the WebView waits for before returning the DOM (JS-rendered content). */
    val waitFor: String? = null,
    val timeoutMs: Long = DEFAULT_WEBVIEW_TIMEOUT_MS,
    val method: FetchMethod = FetchMethod.GET,
    /** Form-urlencoded body sent when [method] is POST (empty form when null). */
    val body: String? = null,
) {
    companion object {
        const val DEFAULT_WEBVIEW_TIMEOUT_MS = 25_000L
    }
}

/** Fetches a web page as text: OkHttp by default, a hidden WebView when the rule asks for `webview`. */
interface HtmlFetcher {
    /**
     * @param charsetHint charset from the source rule, used when neither the HTTP header nor the HTML
     *   declares one (Chinese sites often serve GBK/GB18030 without a header).
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), charsetHint: String? = null): FetchedPage

    /**
     * Submits [body] as an `application/x-www-form-urlencoded` POST and returns the response like [get].
     * Fetchers that cannot submit forms (the WebView) throw [UnsupportedOperationException].
     */
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        charsetHint: String? = null,
    ): FetchedPage = throw UnsupportedOperationException("POST is not supported by ${this::class.simpleName}")

    suspend fun fetch(url: String, options: FetchOptions): FetchedPage = when (options.method) {
        FetchMethod.GET -> get(url, options.headers, options.charsetHint)
        FetchMethod.POST -> post(url, options.body.orEmpty(), options.headers, options.charsetHint)
    }
}

@Singleton
class OkHttpHtmlFetcher @Inject constructor(
    private val client: OkHttpClient,
) : HtmlFetcher {

    override suspend fun get(url: String, headers: Map<String, String>, charsetHint: String?): FetchedPage =
        execute(url, charsetHint) { Request.Builder().url(url).applyHeaders(headers).build() }

    override suspend fun post(url: String, body: String, headers: Map<String, String>, charsetHint: String?): FetchedPage =
        execute(url, charsetHint) {
            // The body is already form-encoded by the rule template; send it verbatim with the form content type.
            Request.Builder().url(url).applyHeaders(headers).post(body.toRequestBody(FORM_URLENCODED)).build()
        }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        headers.forEach { (k, v) -> header(k, v) }
    }

    private suspend fun execute(url: String, charsetHint: String?, request: () -> Request): FetchedPage {
        client.newCall(request()).await().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            val body = response.body
            val bytes = body.bytes()
            val headerCharset = body.contentType()?.charset()
            val charset = headerCharset ?: CharsetSniffer.sniff(bytes) ?: charsetHint?.let(::safeCharset) ?: Charsets.UTF_8
            return FetchedPage(
                requestUrl = url,
                finalUrl = response.request.url.toString(),
                html = String(bytes, charset),
                statusCode = response.code,
                charset = charset.name(),
            )
        }
    }

    private fun safeCharset(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private companion object {
        val FORM_URLENCODED = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    }
}

/** Reads the charset declared in the HTML head (`<meta charset>` or `http-equiv`), if any. */
object CharsetSniffer {
    private val META = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)

    fun sniff(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = META.find(head)?.groupValues?.get(1) ?: return null
        val normalized = when (name.lowercase()) {
            "gb2312", "gbk", "gb_2312-80" -> "GB18030" // GB18030 is a superset; avoids unmappable chars
            else -> name
        }
        return runCatching { Charset.forName(normalized) }.getOrNull()
    }
}

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
