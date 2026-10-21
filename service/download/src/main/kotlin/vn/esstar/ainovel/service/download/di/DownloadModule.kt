package vn.esstar.ainovel.service.download.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.domain.repository.DownloadRepository
import vn.esstar.ainovel.service.download.DownloadRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {
    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
