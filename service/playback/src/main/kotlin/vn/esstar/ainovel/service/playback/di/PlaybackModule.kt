package vn.esstar.ainovel.service.playback.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.esstar.ainovel.domain.playback.PlaybackController
import vn.esstar.ainovel.service.playback.MediaControllerPlaybackController

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    abstract fun bindPlaybackController(impl: MediaControllerPlaybackController): PlaybackController
}
