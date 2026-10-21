package vn.esstar.ainovel.core.network

import java.io.IOException
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
)

class HttpException(val code: Int, val url: String) : IOException("HTTP $code for $url")

/** Fetches a web page as text. Implementations: OkHttp now, WebView-backed later for JS-heavy sites. */
interface HtmlFetcher {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): FetchedPage
}

@Singleton
class OkHttpHtmlFetcher @Inject constructor(
    private val client: OkHttpClient,
) : HtmlFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): FetchedPage {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            val body = response.body.string()
            return FetchedPage(
                requestUrl = url,
                finalUrl = response.request.url.toString(),
                html = body,
                statusCode = response.code,
            )
        }
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
