package vn.esstar.ainovel.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.esstar.ainovel.domain.playback.PlaybackState

/** Persistent mini player shown above the bottom navigation bar. Tap the text to open the full player. */
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MiniPlayerContent(
        state = state,
        onOpenPlayer = onOpenPlayer,
        onPlayPause = viewModel::togglePlayPause,
        onPrevChapter = viewModel::previousChapter,
        onNextChapter = viewModel::nextChapter,
        modifier = modifier,
    )
}

@Composable
internal fun MiniPlayerContent(
    state: PlaybackState,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LinearProgressIndicator(progress = { state.percent }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = state.isActive, onClick = onOpenPlayer),
                ) {
                    Text(
                        text = if (state.isActive) state.bookTitle else stringResource(R.string.player_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.isActive) state.chapterTitle else stringResource(R.string.player_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevChapter, enabled = state.isActive && state.chapterIndex > 0) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_previous_chapter))
                }
                FilledIconButton(onClick = onPlayPause, enabled = state.isActive) {
                    when {
                        state.isBuffering && state.isPlaying -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        state.isPlaying -> Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.player_pause))
                        else -> Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.player_play))
                    }
                }
                IconButton(onClick = onNextChapter, enabled = state.isActive && state.chapterIndex + 1 < state.chapterCount) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next_chapter))
                }
            }
        }
    }
}
