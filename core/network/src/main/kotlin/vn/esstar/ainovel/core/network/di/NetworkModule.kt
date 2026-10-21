package vn.esstar.ainovel.core.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import vn.esstar.ainovel.core.network.HtmlFetcher
import vn.esstar.ainovel.core.network.OkHttpHtmlFetcher

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(InMemoryCookieJar())
        .addInterceptor(UserAgentInterceptor(DEFAULT_USER_AGENT))
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {
    @Binds
    abstract fun bindHtmlFetcher(impl: OkHttpHtmlFetcher): HtmlFetcher
}

/** Adds a browser-like User-Agent unless the request already set one. */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        if (original.header("User-Agent") != null) return chain.proceed(original)
        return chain.proceed(original.newBuilder().header("User-Agent", userAgent).build())
    }
}

/** Keeps session cookies per host for the life of the process (enough for most story sites). */
class InMemoryCookieJar : CookieJar {
    private val store = HashMap<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { c ->
            list.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            list.add(c)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.flatten().filter { it.expiresAt > now && it.matches(url) }
    }
}
