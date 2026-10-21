package vn.esstar.ainovel.feature.library.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import vn.esstar.ainovel.core.designsystem.component.LoadingState
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.feature.library.R

@Composable
internal fun BookDetailRoute(
    onBack: () -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reloadProgress() }
    BookDetailScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggleFavorite = viewModel::toggleFavorite,
        onOpenChapter = { onOpenReader(viewModel.bookId, it.id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailScreen(
    state: BookDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
) {
    val book = state.book
    var descending by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite, enabled = book != null) {
                        Icon(
                            if (book?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.detail_favorite),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = book != null && !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.detail_refresh))
                    }
                },
            )
        },
    ) { padding ->
        if (book == null) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        val chapters = if (descending) state.chapters.asReversed() else state.chapters
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.padding(16.dp)) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(width = 96.dp, height = 132.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge)
                        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
                        book.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (book.genres.isNotEmpty()) {
                            Text(
                                book.genres.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.detail_chapter_count, state.chapters.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(book.sourceId.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val continueChapter = state.continueChapter
                    Button(
                        onClick = { (continueChapter ?: state.chapters.firstOrNull())?.let(onOpenChapter) },
                        enabled = state.chapters.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (continueChapter != null) stringResource(R.string.detail_continue, continueChapter.index + 1)
                            else stringResource(R.string.detail_read_from_start),
                        )
                    }
                    OutlinedButton(onClick = { descending = !descending }) {
                        Icon(Icons.Default.SwapVert, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (descending) R.string.detail_sort_desc else R.string.detail_sort_asc))
                    }
                }
                if (!book.description.isNullOrBlank()) {
                    Text(
                        text = book.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (state.refreshFailed) {
                    Text(
                        stringResource(R.string.detail_refresh_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Text(
                    stringResource(R.string.detail_chapters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            itemsIndexed(chapters, key = { _, c -> c.id }) { _, chapter ->
                val isCurrent = chapter.id == state.progress?.chapterId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChapter(chapter) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (chapter.isCached || chapter.isDownloaded) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (chapter.isDownloaded) R.string.detail_downloaded else R.string.detail_cached),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
