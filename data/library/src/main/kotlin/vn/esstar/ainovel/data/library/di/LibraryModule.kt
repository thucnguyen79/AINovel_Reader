package vn.esstar.ainovel.data.library.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.data.library.BackupRepositoryImpl
import vn.esstar.ainovel.data.library.LibraryRepositoryImpl
import vn.esstar.ainovel.domain.repository.BackupRepository
import vn.esstar.ainovel.domain.repository.LibraryRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
