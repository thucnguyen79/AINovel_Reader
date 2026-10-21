package vn.esstar.ainovel.service.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.core.datastore.TtsPreferences
import vn.esstar.ainovel.domain.playback.PlaybackController
import vn.esstar.ainovel.domain.playback.PlaybackError
import vn.esstar.ainovel.domain.playback.PlaybackState

/**
 * App-side handle on [PlaybackService]. Connects lazily, mirrors the player into [state] and forwards
 * commands. Lives for the whole process so the mini-player sees playback started from anywhere.
 */
@Singleton
class MediaControllerPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsPreferences: TtsPreferences,
) : PlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state

    private var controller: MediaController? = null
    private var connecting = false
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    init {
        connect()
    }

    private fun connect() {
        if (controller != null || connecting) return
        connecting = true
        scope.launch {
            runCatching {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh(controller)
                        override fun onDisconnected(controller: MediaController) {
                            this@MediaControllerPlaybackController.controller = null
                            _state.value = PlaybackState()
                        }
                    })
                    .buildAsync()
                    .await()
            }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                refresh(c)
                while (pendingCommands.isNotEmpty()) pendingCommands.removeFirst().invoke(c)
            }.onFailure { Timber.e(it, "Cannot connect to PlaybackService") }
            connecting = false
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pendingCommands.addLast(block)
            connect()
        }
    }

    private fun refresh(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        val extras = item?.mediaMetadata?.extras
        val sessionExtras = (player as? MediaController)?.sessionExtras
        val error = sessionExtras?.getString(PlaybackCommands.SESSION_ERROR)?.let { runCatching { PlaybackError.valueOf(it) }.getOrNull() }
        val sleepEndsAt = sessionExtras?.takeIf { it.containsKey(PlaybackCommands.SESSION_SLEEP_ENDS_AT) }?.getLong(PlaybackCommands.SESSION_SLEEP_ENDS_AT)
        val buffering = (sessionExtras?.getBoolean(PlaybackCommands.SESSION_BUFFERING) ?: false) ||
            (player.playbackState == Player.STATE_BUFFERING)
        if (extras == null) {
            _state.update {
                PlaybackState(speed = player.playbackParameters.speed, error = error, sleepTimerEndsAt = sleepEndsAt)
            }
            return
        }
        _state.update {
            PlaybackState(
                bookId = extras.getLong(PlaybackCommands.EXTRA_BOOK_ID),
                chapterId = extras.getLong(PlaybackCommands.EXTRA_CHAPTER_ID),
                bookTitle = item.mediaMetadata.artist?.toString() ?: "",
                chapterTitle = item.mediaMetadata.title?.toString() ?: "",
                chapterIndex = extras.getInt(PlaybackCommands.EXTRA_CHAPTER_INDEX),
                chapterCount = extras.getInt(PlaybackCommands.EXTRA_CHAPTER_COUNT),
                segmentIndex = extras.getInt(PlaybackCommands.EXTRA_SEGMENT_INDEX),
                segmentCount = extras.getInt(PlaybackCommands.EXTRA_SEGMENT_COUNT),
                paragraphIndex = extras.getInt(PlaybackCommands.EXTRA_PARAGRAPH_INDEX),
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED),
                isBuffering = buffering,
                speed = player.playbackParameters.speed,
                sleepTimerEndsAt = sleepEndsAt,
                error = error,
            )
        }
    }

    override fun play(bookId: Long, chapterId: Long, paragraphIndex: Int) = withController { c ->
        val args = Bundle().apply {
            putLong(PlaybackCommands.ARG_BOOK_ID, bookId)
            putLong(PlaybackCommands.ARG_CHAPTER_ID, chapterId)
            putInt(PlaybackCommands.ARG_PARAGRAPH_INDEX, paragraphIndex)
        }
        c.sendCustomCommand(SessionCommand(PlaybackCommands.PLAY_CHAPTER, Bundle.EMPTY), args)
    }

    override fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE && c.mediaItemCount > 0) c.prepare()
            c.play()
        }
    }

    override fun pause() = withController { it.pause() }

    override fun stop() = withController { it.sendCustomCommand(SessionCommand(PlaybackCommands.STOP, Bundle.EMPTY), Bundle.EMPTY) }

    override fun nextSegment() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    override fun previousSegment() = withController { c ->
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    override fun nextChapter() = withController { it.sendCustomCommand(SessionCommand(PlaybackCommands.NEXT_CHAPTER, Bundle.EMPTY), Bundle.EMPTY) }

    override fun previousChapter() = withController { it.sendCustomCommand(SessionCommand(PlaybackCommands.PREV_CHAPTER, Bundle.EMPTY), Bundle.EMPTY) }

    override fun seekToSegment(index: Int) = withController { c ->
        c.sendCustomCommand(
            SessionCommand(PlaybackCommands.SEEK_SEGMENT, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackCommands.ARG_SEGMENT_INDEX, index) },
        )
    }

    override fun setSpeed(speed: Float) {
        withController { it.playbackParameters = PlaybackParameters(speed) }
        scope.launch { ttsPreferences.setSpeed(speed) }
    }

    override fun setSleepTimer(minutes: Int) = withController { c ->
        c.sendCustomCommand(
            SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackCommands.ARG_MINUTES, minutes) },
        )
    }
}
