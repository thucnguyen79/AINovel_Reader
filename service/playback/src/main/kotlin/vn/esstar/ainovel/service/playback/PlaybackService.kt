package vn.esstar.ainovel.service.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher
import vn.esstar.ainovel.core.datastore.TtsPreferences
import vn.esstar.ainovel.core.storage.AppStorage
import vn.esstar.ainovel.domain.repository.LibraryRepository
import vn.esstar.ainovel.domain.usecase.GetReadableChapterUseCase
import vn.esstar.ainovel.tts.api.AudioCache
import vn.esstar.ainovel.tts.api.TtsEngine

/**
 * Background text-to-speech player. One MediaItem per sentence; Media3 provides the notification,
 * lock-screen controls and headset buttons. Chapter navigation is exposed as custom session commands.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var engine: TtsEngine
    @Inject lateinit var readableChapter: GetReadableChapterUseCase
    @Inject lateinit var ttsPreferences: TtsPreferences
    @Inject lateinit var storage: AppStorage
    @Inject @Dispatcher(AppDispatcher.IO) lateinit var io: CoroutineDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var pipeline: ChapterAudioPipeline
    private var session: MediaSession? = null
    private var sleepJob: Job? = null
    private var sleepEndsAt: Long? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        pipeline = ChapterAudioPipeline(
            scope = scope,
            player = player,
            library = library,
            contentProvider = { readableChapter(it) },
            engine = engine,
            cache = AudioCache(storage.audioCache),
            io = io,
            prefsProvider = { ttsPreferences.current() },
        )

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                pipeline.onItemStarted(mediaItem)
            }
        })

        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .apply { sessionActivity?.let(::setSessionActivity) }
            .build()
            .also { it.setMediaButtonPreferences(chapterButtons()) }

        scope.launch {
            val prefs = ttsPreferences.current()
            player.setPlaybackSpeed(prefs.speed)
        }
        // Publish pipeline status (buffering / errors) and the sleep timer through session extras.
        scope.launch {
            pipeline.status.collect { publishExtras() }
        }
        // Voice / pitch / comma changes restart the current chapter from the current sentence.
        scope.launch {
            ttsPreferences.prefs
                .map { Triple(it.voiceId, it.pitch, it.breakAtComma) }
                .distinctUntilChanged()
                .drop(1)
                .collect { restartFromCurrent() }
        }
        scope.launch {
            ttsPreferences.prefs.map { it.speed }.distinctUntilChanged().collect { if (player.playbackParameters.speed != it) player.setPlaybackSpeed(it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        pipeline.stop()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun restartFromCurrent() {
        val extras = player.currentMediaItem?.mediaMetadata?.extras ?: return
        val bookId = extras.getLong(PlaybackCommands.EXTRA_BOOK_ID, -1)
        val chapterId = extras.getLong(PlaybackCommands.EXTRA_CHAPTER_ID, -1)
        val segment = extras.getInt(PlaybackCommands.EXTRA_SEGMENT_INDEX, 0)
        if (bookId >= 0 && chapterId >= 0) pipeline.start(bookId, chapterId, segmentIndex = segment)
    }

    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAt = null
        if (minutes > 0) {
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            sleepEndsAt = endsAt
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                player.pause()
                sleepEndsAt = null
                publishExtras()
            }
        }
        publishExtras()
    }

    private fun publishExtras() {
        val status = pipeline.status.value
        val extras = Bundle().apply {
            putBoolean(PlaybackCommands.SESSION_BUFFERING, status.isBuffering)
            status.error?.let { putString(PlaybackCommands.SESSION_ERROR, it.name) }
            sleepEndsAt?.let { putLong(PlaybackCommands.SESSION_SLEEP_ENDS_AT, it) }
        }
        session?.setSessionExtras(extras)
    }

    private fun chapterButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName(getString(R.string.playback_prev_chapter))
            .setSessionCommand(SessionCommand(PlaybackCommands.PREV_CHAPTER, Bundle.EMPTY))
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(getString(R.string.playback_next_chapter))
            .setSessionCommand(SessionCommand(PlaybackCommands.NEXT_CHAPTER, Bundle.EMPTY))
            .build(),
    )

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.PLAY_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.NEXT_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.PREV_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.SEEK_SEGMENT, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.STOP, Bundle.EMPTY))
                .build()
            // Without seek-back/forward the system media controls show previous/next item, i.e. sentence
            // navigation, which is what listeners expect; chapter buttons come from the custom layout.
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_BACK)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setMediaButtonPreferences(chapterButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.PLAY_CHAPTER -> pipeline.start(
                    bookId = args.getLong(PlaybackCommands.ARG_BOOK_ID),
                    chapterId = args.getLong(PlaybackCommands.ARG_CHAPTER_ID),
                    paragraphIndex = args.getInt(PlaybackCommands.ARG_PARAGRAPH_INDEX, 0),
                )
                PlaybackCommands.NEXT_CHAPTER -> pipeline.nextChapter()
                PlaybackCommands.PREV_CHAPTER -> pipeline.previousChapter()
                PlaybackCommands.SEEK_SEGMENT -> pipeline.seekToSegment(args.getInt(PlaybackCommands.ARG_SEGMENT_INDEX, 0))
                PlaybackCommands.SET_SLEEP_TIMER -> setSleepTimer(args.getInt(PlaybackCommands.ARG_MINUTES, 0))
                PlaybackCommands.STOP -> {
                    pipeline.stop()
                    player.stop()
                    player.clearMediaItems()
                    setSleepTimer(0)
                }
                else -> {
                    Timber.w("Unknown command %s", customCommand.customAction)
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
