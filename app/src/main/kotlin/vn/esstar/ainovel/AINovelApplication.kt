package vn.esstar.ainovel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.esstar.ainovel.core.common.di.ApplicationScope
import vn.esstar.ainovel.core.datastore.NetworkPreferences
import vn.esstar.ainovel.core.network.SwitchableDns
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber
import vn.esstar.ainovel.core.common.log.FileLoggingTree

@HiltAndroidApp
class AINovelApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var switchableDns: SwitchableDns

    @Inject
    lateinit var networkPreferences: NetworkPreferences

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var fileLoggingTree: FileLoggingTree

    /** Covers are loaded through the same OkHttp client as pages, so they carry the browser User-Agent. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(fileLoggingTree)
        Timber.i("App start %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        appScope.launch {
            networkPreferences.dohMode.collect { switchableDns.mode = it }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
