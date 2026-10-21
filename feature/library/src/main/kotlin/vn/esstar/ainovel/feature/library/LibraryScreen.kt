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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.EmptyState
import vn.esstar.ainovel.domain.model.HistoryEntry

@Composable
internal fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LibraryScreen(
        history = history,
        onOpenBook = onOpenBook,
        onOpenReader = onOpenReader,
        onRemoveHistory = viewModel::removeFromHistory,
    )
}

@Composable
internal fun LibraryScreen(
    history: List<HistoryEntry>,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    onRemoveHistory: (Long) -> Unit,
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
            1 -> EmptyState(
                title = stringResource(R.string.library_downloads_empty),
                description = stringResource(R.string.library_downloads_hint),
            )
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
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, onOpenBook: () -> Unit, onRemove: () -> Unit) {
    AINovelCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.book.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
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
