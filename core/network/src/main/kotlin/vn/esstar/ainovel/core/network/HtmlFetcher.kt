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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class FetchedPage(
    val requestUrl: String,
    val finalUrl: String,
    val html: String,
    val statusCode: Int,
    val charset: String = "UTF-8",
)

class HttpException(val code: Int, val url: String) : IOException("HTTP $code for $url")

/** Fetches a web page as text. Implementations: OkHttp now, WebView-backed later for JS-heavy sites. */
interface HtmlFetcher {
    /**
     * @param charsetHint charset from the source rule, used when neither the HTTP header nor the HTML
     *   declares one (Chinese sites often serve GBK/GB18030 without a header).
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), charsetHint: String? = null): FetchedPage
}

@Singleton
class OkHttpHtmlFetcher @Inject constructor(
    private val client: OkHttpClient,
) : HtmlFetcher {

    override suspend fun get(url: String, headers: Map<String, String>, charsetHint: String?): FetchedPage {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).await().use { response ->
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
