package vn.esstar.ainovel.feature.settings

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.domain.model.AppInfo
import vn.esstar.ainovel.domain.model.AppUpdate

/**
 * Looks up the latest GitHub release of the app (public API, no key, no tracking) and compares its tag
 * with the installed version. Only runs when the user taps "Kiểm tra bản mới".
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val appInfo: AppInfo,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) {
    @Serializable
    private data class Asset(val name: String, val browser_download_url: String)

    @Serializable
    private data class Release(val tag_name: String, val html_url: String, val body: String? = null, val assets: List<Asset> = emptyList(), val prerelease: Boolean = false)

    /** Null when the installed build is current (or newer). */
    suspend fun check(): AppUpdate? = withContext(io) {
        val url = appInfo.repositoryUrl.trimEnd('/').replace("https://github.com/", "https://api.github.com/repos/") + "/releases/latest"
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        val release = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            json.decodeFromString<Release>(response.body.string())
        }
        val remote = release.tag_name.removePrefix("v")
        if (compareVersions(remote, appInfo.versionName.substringBefore('-')) <= 0) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && it.name.contains("arm64") }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }
        AppUpdate(versionName = remote, releaseUrl = release.html_url, apkUrl = apk?.browser_download_url, notes = release.body)
    }

    companion object {
        /** Numeric dot-separated compare: "1.2.0" > "1.10" is false, "1.10" > "1.9" is true. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
                if (c != 0) return c
            }
            return 0
        }
    }
}
