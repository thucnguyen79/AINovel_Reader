package vn.esstar.ainovel.feature.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import vn.esstar.ainovel.core.datastore.ReaderPrefs
import vn.esstar.ainovel.core.designsystem.component.ErrorState
import vn.esstar.ainovel.core.designsystem.component.LoadingState
import vn.esstar.ainovel.domain.model.Chapter
import vn.esstar.ainovel.domain.model.TranslateMode
import androidx.compose.material.icons.filled.Headphones

@Composable
internal fun ReaderRoute(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderScreen(
        state = state,
        onBack = onBack,
        onListen = { firstVisible -> viewModel.listen(firstVisible); onOpenPlayer() },
        onPrev = viewModel::prev,
        onNext = viewModel::next,
        onOpenChapter = { viewModel.open(it.id) },
        onRetry = viewModel::retry,
        onScroll = viewModel::onScroll,
        onFontSize = viewModel::setFontSize,
        onTranslateMode = viewModel::setTranslateMode,
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onListen: (firstVisibleIndex: Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
    onRetry: () -> Unit,
    onScroll: (Int, Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onTranslateMode: (TranslateMode) -> Unit,
) {
    var showTranslateMenu by remember { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val listState = remember(state.content?.chapterId) {
        LazyListState(state.restoreIndex.coerceAtLeast(0), state.restoreOffset.coerceAtLeast(0))
    }

    LaunchedEffect(listState, state.content?.chapterId) {
        if (state.content == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(400)
            .collect { (index, offset) -> onScroll(index, offset) }
    }

    Scaffold(
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.content?.title?.takeIf { it.isNotBlank() } ?: state.currentChapter?.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.book?.title ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
                        }
                    },
                    actions = {
                        if (state.translateMode != null) {
                            IconButton(onClick = { showTranslateMenu = true }) {
                                Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.reader_translate_mode))
                            }
                            DropdownMenu(expanded = showTranslateMenu, onDismissRequest = { showTranslateMenu = false }) {
                                TranslateMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.label())) },
                                        trailingIcon = if (mode == state.translateMode) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick = { showTranslateMenu = false; onTranslateMode(mode) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onListen(listState.firstVisibleItemIndex) }, enabled = state.content != null) {
                            Icon(Icons.Default.Headphones, contentDescription = stringResource(R.string.reader_listen))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (chromeVisible) {
                ReaderBottomBar(
                    state = state,
                    onPrev = onPrev,
                    onNext = onNext,
                    onChapters = { showChapters = true },
                    onFontSize = onFontSize,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding), message = stringResource(R.string.reader_loading))
            state.error || state.content == null -> ErrorState(
                message = stringResource(R.string.reader_error),
                modifier = Modifier.padding(padding),
                retryLabel = stringResource(R.string.reader_retry),
                onRetry = onRetry,
            )
            else -> ChapterBody(
                paragraphs = state.content.paragraphs,
                title = state.content.title.takeIf { it.isNotBlank() } ?: state.currentChapter?.title,
                prefs = state.prefs,
                listState = listState,
                hasNext = state.hasNext,
                onNext = onNext,
                onTap = { chromeVisible = !chromeVisible },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showChapters) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showChapters = false }, sheetState = sheetState) {
            ChapterList(
                chapters = state.chapters,
                currentId = state.currentChapter?.id,
                onSelect = { showChapters = false; onOpenChapter(it) },
            )
        }
    }
}

@Composable
private fun ChapterBody(
    paragraphs: List<String>,
    title: String?,
    prefs: ReaderPrefs,
    listState: LazyListState,
    hasNext: Boolean,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = prefs.fontSizeSp.sp
    val lineHeight = (prefs.fontSizeSp * prefs.lineHeight).sp
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onTap),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            item(key = "title") {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        itemsIndexed(paragraphs, key = { i, _ -> i }) { _, paragraph ->
            Text(text = paragraph, fontSize = fontSize, lineHeight = lineHeight, color = MaterialTheme.colorScheme.onBackground)
        }
        item(key = "end") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                if (hasNext) {
                    Text(
                        stringResource(R.string.reader_next_chapter),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onNext).padding(12.dp),
                    )
                } else {
                    Text(stringResource(R.string.reader_last_chapter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFontSize: (Int) -> Unit,
) {
    BottomAppBar {
        IconButton(onClick = onPrev, enabled = state.hasPrev) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.reader_prev_chapter))
        }
        IconButton(onClick = onChapters) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_chapters))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onFontSize(state.prefs.fontSizeSp - 1) }, enabled = state.prefs.fontSizeSp > ReaderPrefs.MIN_FONT) {
            Icon(Icons.Default.TextDecrease, contentDescription = stringResource(R.string.reader_font_smaller))
        }
        Text("${state.prefs.fontSizeSp}", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onFontSize(state.prefs.fontSizeSp + 1) }, enabled = state.prefs.fontSizeSp < ReaderPrefs.MAX_FONT) {
            Icon(Icons.Default.TextIncrease, contentDescription = stringResource(R.string.reader_font_larger))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.reader_next_chapter))
        }
    }
}

@Composable
private fun ChapterList(chapters: List<Chapter>, currentId: Long?, onSelect: (Chapter) -> Unit) {
    val currentIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    Text(
        stringResource(R.string.reader_chapters),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        items(chapters, key = { it.id }) { chapter ->
            val isCurrent = chapter.id == currentId
            Text(
                chapter.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

private fun TranslateMode.label(): Int = when (this) {
    TranslateMode.CONVERT -> R.string.reader_translate_convert
    TranslateMode.HAN_VIET -> R.string.reader_translate_hanviet
    TranslateMode.RAW -> R.string.reader_translate_raw
}
