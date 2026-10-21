package vn.esstar.ainovel.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.network.di.InMemoryCookieJar

/**
 * Loads a page in a hidden WebView so JavaScript runs (client-rendered chapter lists,
 * "verifying your browser" interstitials, WAF cookies) and returns the rendered DOM.
 *
 * One page at a time; cookies the page sets are copied into the OkHttp jar so later
 * plain HTTP requests to the same host can reuse them.
 */
@Singleton
class WebViewHtmlFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookieJar: InMemoryCookieJar,
    @Dispatcher(AppDispatcher.Main) private val main: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var webView: WebView? = null

    suspend fun get(url: String, options: FetchOptions): FetchedPage = mutex.withLock {
        withContext(main) { load(url, options) }
    }

    private suspend fun load(url: String, options: FetchOptions): FetchedPage {
        val view = obtain()
        val firstLoad = CompletableDeferred<Unit>()
        var finalUrl = url
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String?) {
                finalUrl = u ?: finalUrl
                if (!firstLoad.isCompleted) firstLoad.complete(Unit)
            }

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !firstLoad.isCompleted) {
                    firstLoad.completeExceptionally(IOException("WebView error ${error.errorCode} for ${request.url}"))
                }
            }

            /** The renderer died (OOM / crash): drop this WebView and let the next fetch create a fresh one. */
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                Timber.w("WebView renderer gone (crash=%s)", detail.didCrash())
                if (webView === v) webView = null
                runCatching { v.destroy() }
                if (!firstLoad.isCompleted) firstLoad.completeExceptionally(IOException("WebView renderer gone"))
                return true
            }
        }
        Timber.d("WebView fetch %s (waitFor=%s)", url, options.waitFor)
        // WebView ignores a User-Agent passed as an extra header; it must go through settings.
        val userAgent = options.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
            ?: NetworkConfig.USER_AGENT
        if (view.settings.userAgentString != userAgent) view.settings.userAgentString = userAgent
        applyClientHints(view, userAgent)
        val extraHeaders = options.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        view.stopLoading()
        view.loadUrl(url, extraHeaders)
        try {
            withTimeout(options.timeoutMs) {
                firstLoad.await()
                while (!isReady(view, options.waitFor)) delay(POLL_MS)
                delay(SETTLE_MS)
            }
        } catch (e: TimeoutCancellationException) {
            val state = evaluate(view, "location.href + ' | ' + document.readyState + ' | ' + document.title + ' | loaded=' + (!!window.__NUXT__) + ' | body=' + document.body.innerHTML.length")
            Timber.w("WebView fetch timed out after %d ms: %s (%s, firstLoad=%s)", options.timeoutMs, url, state, firstLoad.isCompleted)
            // Fall through: return whatever has rendered so far; the parser reports what is missing.
        }
        val html = evaluate(view, "document.documentElement.outerHTML") ?: throw IOException("Empty DOM for $url")
        syncCookies(finalUrl)
        view.loadUrl("about:blank")
        return FetchedPage(requestUrl = url, finalUrl = finalUrl, html = html, statusCode = 200, charset = "UTF-8")
    }

    private suspend fun isReady(view: WebView, waitFor: String?): Boolean {
        val selector = waitFor?.let { JSONObject.quote(it) }
        // Ads and long-polling keep readyState at "interactive" on many sites, so do not wait for "complete".
        val script = if (selector == null) {
            "document.readyState !== 'loading'"
        } else {
            "document.readyState !== 'loading' && !!document.querySelector($selector)"
        }
        return evaluate(view, script) == "true"
    }

    /** Runs [script] and returns its result as a plain string (JS strings are unquoted). */
    private suspend fun evaluate(view: WebView, script: String): String? = suspendCancellableCoroutine { cont ->
        view.evaluateJavascript(script) { raw ->
            val value = runCatching {
                when (val parsed = JSONTokener(raw ?: "null").nextValue()) {
                    null, JSONObject.NULL -> null
                    else -> parsed.toString()
                }
            }.getOrNull()
            cont.resume(value)
        }
    }

    private fun syncCookies(url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val header = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() ?: return
        cookieJar.saveRawCookies(httpUrl, header)
    }

    /**
     * Sites decide mobile vs desktop from Client Hints (`Sec-CH-UA-Mobile`) as much as from the UA string,
     * so a desktop UA must come with desktop hints or the site still redirects to its m./h5. host.
     */
    private fun applyClientHints(view: WebView, userAgent: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
        val desktop = "Windows" in userAgent || "Macintosh" in userAgent || "X11" in userAgent
        runCatching {
            val current = WebSettingsCompat.getUserAgentMetadata(view.settings)
            val metadata = UserAgentMetadata.Builder(current)
                .setMobile(!desktop)
                .setPlatform(if (desktop) "Windows" else "Android")
                .setPlatformVersion(if (desktop) "10.0.0" else current.platformVersion)
                .build()
            WebSettingsCompat.setUserAgentMetadata(view.settings, metadata)
        }.onFailure { Timber.w(it, "Cannot set UA metadata") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun obtain(): WebView = webView ?: WebView(context).apply {
        // Not attached to a window: give it a desktop-sized layout so width-based mobile checks pass.
        measure(
            View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
        settings.userAgentString = NetworkConfig.USER_AGENT
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webView = this
    }

    private companion object {
        const val POLL_MS = 250L
        const val SETTLE_MS = 400L
        const val LAYOUT_WIDTH = 1280
        const val LAYOUT_HEIGHT = 1024
    }
}
