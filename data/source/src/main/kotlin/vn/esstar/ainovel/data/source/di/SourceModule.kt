package vn.esstar.ainovel.data.source.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.data.source.SourceRepositoryImpl
import vn.esstar.ainovel.data.source.registry.AssetRuleLoader
import vn.esstar.ainovel.data.source.registry.RuleLoader
import vn.esstar.ainovel.domain.repository.SourceRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {
    @Binds
    abstract fun bindRuleLoader(impl: AssetRuleLoader): RuleLoader

    @Binds
    abstract fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository
}
