package vn.esstar.ainovel.core.network

import javax.inject.Inject
import javax.inject.Singleton

/** Picks OkHttp or the hidden WebView per request, as the source rule asks. */
@Singleton
class RoutingHtmlFetcher @Inject constructor(
    private val http: OkHttpHtmlFetcher,
    private val webView: WebViewHtmlFetcher,
) : HtmlFetcher {

    override suspend fun get(url: String, headers: Map<String, String>, charsetHint: String?): FetchedPage =
        http.get(url, headers, charsetHint)

    override suspend fun post(url: String, body: String, headers: Map<String, String>, charsetHint: String?): FetchedPage =
        http.post(url, body, headers, charsetHint)

    override suspend fun fetch(url: String, options: FetchOptions): FetchedPage = when {
        // A hidden WebView cannot submit a form body: AJAX TOC endpoints are POSTed over OkHttp even in webview rules.
        options.method == FetchMethod.POST -> http.post(url, options.body.orEmpty(), options.headers, options.charsetHint)
        options.mode == FetchMode.WEBVIEW -> webView.get(url, options)
        else -> http.get(url, options.headers, options.charsetHint)
    }
}
