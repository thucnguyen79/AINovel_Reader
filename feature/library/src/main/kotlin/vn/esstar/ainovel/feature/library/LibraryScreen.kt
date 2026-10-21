package vn.esstar.ainovel.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.EmptyState
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.core.designsystem.component.BookCover
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import vn.esstar.ainovel.domain.model.BookDownloadInfo

@Composable
internal fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedBooks.collectAsStateWithLifecycle()
    LibraryScreen(
        history = history,
        downloaded = downloaded,
        onOpenBook = onOpenBook,
        onOpenReader = onOpenReader,
        onRemoveHistory = viewModel::removeFromHistory,
        onCancelDownload = viewModel::cancelDownload,
        onDeleteDownloaded = viewModel::deleteDownloaded,
    )
}

@Composable
internal fun LibraryScreen(
    history: List<HistoryEntry>,
    downloaded: List<BookDownloadInfo>,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    onRemoveHistory: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onDeleteDownloaded: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        stringResource(R.string.library_tab_history),
        stringResource(R.string.library_tab_downloads),
        stringResource(R.string.library_tab_ebooks),
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(label) })
            }
        }
        when (selected) {
            0 -> HistoryTab(history, onOpenBook, onOpenReader, onRemoveHistory)
            1 -> DownloadsTab(downloaded, onOpenBook, onCancelDownload, onDeleteDownloaded)
            else -> EmptyState(
                title = stringResource(R.string.library_empty),
                description = stringResource(R.string.library_ebook_hint),
            )
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<HistoryEntry>,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    onRemoveHistory: (Long) -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_history_empty),
            description = stringResource(R.string.library_history_hint),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(history, key = { it.book.id }) { entry ->
            HistoryRow(
                entry = entry,
                onClick = {
                    val chapter = entry.lastChapter
                    if (chapter != null) onOpenReader(entry.book.id, chapter.id) else onOpenBook(entry.book.id)
                },
                onOpenBook = { onOpenBook(entry.book.id) },
                onRemove = { onRemoveHistory(entry.book.id) },
            )
        }
    }
}

@Composable
private fun DownloadsTab(
    books: List<BookDownloadInfo>,
    onOpenBook: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onDeleteDownloaded: (Long) -> Unit,
) {
    if (books.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_downloads_empty),
            description = stringResource(R.string.library_downloads_hint),
        )
        return
    }
    var confirmDelete by remember { mutableStateOf<BookDownloadInfo?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { it.book.id }) { info ->
            AINovelCard(modifier = Modifier.fillMaxWidth().clickable { onOpenBook(info.book.id) }, contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(url = info.book.coverUrl, refererUrl = info.book.url, modifier = Modifier.size(width = 52.dp, height = 72.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(info.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.library_download_summary, info.downloadedCount, info.chapterCount, formatBytes(info.sizeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (info.cachedCount > info.downloadedCount) {
                            Text(
                                stringResource(R.string.library_cached_summary, info.cachedCount - info.downloadedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = info }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.library_delete_downloaded))
                    }
                }
                info.activeJob?.let { job ->
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.download_progress, job.completed, job.total),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                        TextButton(onClick = { onCancelDownload(job.id) }) { Text(stringResource(R.string.download_cancel)) }
                    }
                }
            }
        }
    }
    confirmDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.library_delete_downloaded)) },
            text = { Text(stringResource(R.string.library_delete_confirm, info.book.title)) },
            confirmButton = {
                TextButton(onClick = { onDeleteDownloaded(info.book.id); confirmDelete = null }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.library_keep)) } },
        )
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(java.util.Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onOpenBook: () -> Unit, onRemove: () -> Unit) {
    AINovelCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                url = entry.book.coverUrl,
                refererUrl = entry.book.url,
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clickable(onClick = onOpenBook),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.lastChapter?.let {
                    Text(it.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = stringResource(
                        R.string.library_last_read_at,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.lastAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.library_remove_history))
            }
        }
    }
}
