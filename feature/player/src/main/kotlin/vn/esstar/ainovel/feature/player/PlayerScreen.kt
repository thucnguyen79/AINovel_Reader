package vn.esstar.ainovel.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import vn.esstar.ainovel.core.designsystem.component.BookCover
import vn.esstar.ainovel.domain.model.Book
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.playback.PlaybackError
import vn.esstar.ainovel.domain.playback.PlaybackState

@Composable
internal fun PlayerRoute(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    PlayerScreen(
        state = state,
        book = book,
        chapters = chapters,
        onBack = onBack,
        onPlayPause = viewModel::togglePlayPause,
        onPrevSegment = viewModel::previousSegment,
        onNextSegment = viewModel::nextSegment,
        onPrevChapter = viewModel::previousChapter,
        onNextChapter = viewModel::nextChapter,
        onSeekSegment = viewModel::seekToSegment,
        onSpeed = viewModel::setSpeed,
        onSleepTimer = viewModel::setSleepTimer,
        onStop = { viewModel.stop(); onBack() },
        onPlayChapter = viewModel::playChapter,
    )
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f)
private val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlayerScreen(
    state: PlaybackState,
    book: Book?,
    chapters: List<Chapter>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevSegment: () -> Unit,
    onNextSegment: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekSegment: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onStop: () -> Unit,
    onPlayChapter: (Chapter) -> Unit,
) {
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse))
                    }
                },
                actions = {
                    IconButton(onClick = onStop, enabled = state.isActive) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.player_stop))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookCover(
                url = book?.coverUrl,
                refererUrl = book?.url,
                cornerRadius = 16.dp,
                modifier = Modifier.size(width = 160.dp, height = 220.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.bookTitle.ifBlank { book?.title ?: "" },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.chapterTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { showChapters = true },
            )
            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            PlaybackError.TTS_UNAVAILABLE -> R.string.player_error_tts
                            PlaybackError.CHAPTER_LOAD_FAILED -> R.string.player_error_chapter
                            PlaybackError.SYNTHESIS_FAILED -> R.string.player_error_synthesis
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            SegmentSlider(state = state, onSeekSegment = onSeekSegment)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevChapter, enabled = state.isActive && state.chapterIndex > 0) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous_chapter), modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onPrevSegment, enabled = state.isActive) {
                    Icon(Icons.Default.FastRewind, contentDescription = stringResource(R.string.player_previous_segment), modifier = Modifier.size(32.dp))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = state.isActive,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    when {
                        state.isBuffering && state.isPlaying -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                        state.isPlaying -> Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.player_pause), modifier = Modifier.size(36.dp))
                        else -> Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play), modifier = Modifier.size(36.dp))
                    }
                }
                IconButton(onClick = onNextSegment, enabled = state.isActive) {
                    Icon(Icons.Default.FastForward, contentDescription = stringResource(R.string.player_next_segment), modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onNextChapter, enabled = state.isActive && state.chapterIndex + 1 < state.chapterCount) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next_chapter), modifier = Modifier.size(32.dp))
                }
            }

            Text(stringResource(R.string.player_speed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = kotlin.math.abs(state.speed - speed) < 0.01f,
                        onClick = { onSpeed(speed) },
                        label = { Text(formatSpeed(speed)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.player_sleep_timer), modifier = Modifier.weight(1f))
                TextButton(onClick = { showSleepMenu = true }) {
                    Text(
                        state.sleepTimerEndsAt?.let {
                            stringResource(R.string.player_sleep_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                        } ?: stringResource(R.string.player_sleep_off),
                    )
                }
                DropdownMenu(expanded = showSleepMenu, onDismissRequest = { showSleepMenu = false }) {
                    SLEEP_OPTIONS.forEach { minutes ->
                        DropdownMenuItem(
                            text = {
                                Text(if (minutes == 0) stringResource(R.string.player_sleep_off) else stringResource(R.string.player_sleep_minutes, minutes))
                            },
                            onClick = { showSleepMenu = false; onSleepTimer(minutes) },
                        )
                    }
                }
            }
            TextButton(onClick = { showChapters = true }, enabled = chapters.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.player_chapters, state.chapterIndex + 1, state.chapterCount))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showChapters) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showChapters = false }, sheetState = sheetState) {
            ChapterPicker(chapters = chapters, currentId = state.chapterId, onSelect = { showChapters = false; onPlayChapter(it) })
        }
    }
}

@Composable
private fun SegmentSlider(state: PlaybackState, onSeekSegment: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.segmentIndex) { if (!dragging) dragValue = state.segmentIndex.toFloat() }
    val max = (state.segmentCount - 1).coerceAtLeast(1).toFloat()
    Slider(
        value = if (dragging) dragValue else state.segmentIndex.toFloat().coerceIn(0f, max),
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { dragging = false; onSeekSegment(dragValue.toInt()) },
        valueRange = 0f..max,
        enabled = state.isActive && state.segmentCount > 1,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.player_segment_position, state.segmentIndex + 1, state.segmentCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${(state.percent * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChapterPicker(chapters: List<Chapter>, currentId: Long?, onSelect: (Chapter) -> Unit) {
    val currentIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    Text(
        stringResource(R.string.player_chapter_list),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        items(chapters, key = { it.id }) { chapter ->
            Text(
                chapter.title,
                color = if (chapter.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(chapter) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private fun formatSpeed(speed: Float): String = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
