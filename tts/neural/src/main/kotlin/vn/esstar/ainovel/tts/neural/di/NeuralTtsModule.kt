package vn.esstar.ainovel.tts.neural.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.tts.api.TtsEngine
import vn.esstar.ainovel.tts.api.VoiceModelManager
import vn.esstar.ainovel.tts.neural.CompositeTtsEngine
import vn.esstar.ainovel.tts.neural.NeuralModelManager

@Module
@InstallIn(SingletonComponent::class)
abstract class NeuralTtsModule {
    /** The app-wide engine chooses system or neural per voice; `tts/system` no longer binds TtsEngine. */
    @Binds
    abstract fun bindTtsEngine(impl: CompositeTtsEngine): TtsEngine

    @Binds
    abstract fun bindVoiceModelManager(impl: NeuralModelManager): VoiceModelManager
}
