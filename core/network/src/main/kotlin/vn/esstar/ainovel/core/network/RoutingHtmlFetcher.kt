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

    override suspend fun fetch(url: String, options: FetchOptions): FetchedPage = when (options.mode) {
        FetchMode.HTTP -> http.get(url, options.headers, options.charsetHint)
        FetchMode.WEBVIEW -> webView.get(url, options)
    }
}
