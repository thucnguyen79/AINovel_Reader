package vn.esstar.ainovel.data.ebook.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.data.ebook.EbookRepositoryImpl
import vn.esstar.ainovel.domain.repository.EbookRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class EbookModule {
    @Binds
    abstract fun bindEbookRepository(impl: EbookRepositoryImpl): EbookRepository
}
