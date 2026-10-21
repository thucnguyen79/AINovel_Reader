package vn.esstar.ainovel.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.esstar.ainovel.BuildConfig
import vn.esstar.ainovel.core.common.log.FileLoggingTree
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.domain.model.AppInfo

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    const val REPOSITORY_URL = "https://github.com/thucnguyen79/AINovel_Reader"

    @Provides
    @Singleton
    fun provideFileLoggingTree(storage: AppStorage): FileLoggingTree = FileLoggingTree(storage.logs)

    @Provides
    @Singleton
    fun provideAppInfo(): AppInfo = AppInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        isDebug = BuildConfig.DEBUG,
        repositoryUrl = REPOSITORY_URL,
    )
}
