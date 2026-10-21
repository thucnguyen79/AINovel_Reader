package vn.esstar.ainovel.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.SectionTitle
import vn.esstar.ainovel.domain.model.SearchResult
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.core.designsystem.component.BookCover

@Composable
internal fun HomeRoute(
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenBook -> onOpenBook(event.bookId)
                is HomeEvent.OpenReader -> onOpenReader(event.bookId, event.chapterId)
                is HomeEvent.OpenBrowser -> onOpenBrowser(event.url)
                is HomeEvent.ShowMessage -> snackbar.showSnackbar(context.getString(event.message.res()))
            }
        }
    }

    HomeScreen(
        state = state,
        snackbar = snackbar,
        onInputChange = viewModel::onInputChange,
        onSubmit = { viewModel.submit() },
        onPaste = { text -> if (text.isNullOrBlank()) viewModel.onClipboardEmpty() else viewModel.submit(text) },
        onResultClick = { viewModel.open(it.url) },
        onClearResults = viewModel::clearResults,
        onSiteClick = viewModel::openSite,
    )
}

private fun UiMessage.res(): Int = when (this) {
    UiMessage.UNSUPPORTED_SITE -> R.string.home_msg_unsupported
    UiMessage.OPEN_FAILED -> R.string.home_msg_open_failed
    UiMessage.SEARCH_FAILED -> R.string.home_msg_search_failed
    UiMessage.EMPTY_INPUT -> R.string.home_msg_empty_input
    UiMessage.CLIPBOARD_EMPTY -> R.string.home_msg_clipboard_empty
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: (String?) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onClearResults: () -> Unit,
    onSiteClick: (SourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AINovelCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.home_paste_link_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        trailingIcon = {
                            if (state.input.isNotEmpty()) {
                                IconButton(onClick = { onInputChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_clear))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val text = runCatching {
                                        clipboard.getClipEntry()?.clipData?.getItemAt(0)?.coerceToText(context)?.toString()
                                    }.getOrNull()
                                    onPaste(text)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_paste_link))
                        }
                        Button(onClick = onSubmit, enabled = !state.isOpening, modifier = Modifier.weight(1f)) {
                            if (state.isOpening) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_search))
                        }
                    }
                }
            }

            if (state.searchedQuery != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            text = stringResource(R.string.home_results_for, state.searchedQuery),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearResults) { Text(stringResource(R.string.home_clear)) }
                    }
                }
                if (state.isSearching) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(8.dp)) }
                } else if (state.results.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.results, key = { it.sourceId.value + it.url }) { result ->
                    SearchResultRow(result = result, onClick = { onResultClick(result) })
                }
            }

            item { SectionTitle(stringResource(R.string.home_supported_sites)) }
            item {
                if (state.sources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_supported_sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.sources.forEach { source ->
                            AssistChip(onClick = { onSiteClick(source) }, label = { Text(source.hosts.first()) })
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    AINovelCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                url = result.coverUrl,
                refererUrl = result.url,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!result.author.isNullOrBlank()) {
                    Text(result.author!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val meta = listOfNotNull(result.latestChapter, result.sourceId.value).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
