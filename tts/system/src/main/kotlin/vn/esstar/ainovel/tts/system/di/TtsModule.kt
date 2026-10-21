package vn.esstar.ainovel.tts.system.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.system.SystemTtsEngine

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    /** Phase 2: the system engine is the only one. Phase 5 adds a selector over several engines. */
    @Binds
    abstract fun bindTtsEngine(impl: SystemTtsEngine): TtsEngine
}
