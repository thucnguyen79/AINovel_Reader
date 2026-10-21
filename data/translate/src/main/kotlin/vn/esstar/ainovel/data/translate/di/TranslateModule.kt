package vn.esstar.ainovel.data.translate.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.data.translate.TranslationRepositoryImpl
import vn.esstar.ainovel.domain.repository.TranslationRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslateModule {
    @Binds
    abstract fun bindTranslationRepository(impl: TranslationRepositoryImpl): TranslationRepository
}
