package vn.esstar.ainovel.feature.translate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.BookCover
import vn.esstar.ainovel.core.designsystem.component.SectionTitle
import vn.esstar.ainovel.domain.model.DictionaryInfo
import vn.esstar.ainovel.domain.model.DictionaryType
import vn.esstar.ainovel.domain.model.HistoryEntry
import vn.esstar.ainovel.domain.model.SourceInfo
import vn.esstar.ainovel.domain.model.TranslateMode

@Composable
internal fun TranslateRoute(
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    viewModel: TranslateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TranslateEvent.OpenBook -> onOpenBook(event.bookId)
                is TranslateEvent.OpenReader -> onOpenReader(event.bookId, event.chapterId)
                is TranslateEvent.OpenBrowser -> onOpenBrowser(event.url)
                is TranslateEvent.ShowMessage -> snackbar.showSnackbar(
                    when (val m = event.message) {
                        TranslateMessage.EmptyInput -> context.getString(R.string.translate_msg_empty_input)
                        TranslateMessage.ClipboardEmpty -> context.getString(R.string.translate_msg_clipboard_empty)
                        TranslateMessage.Unsupported -> context.getString(R.string.translate_msg_unsupported)
                        TranslateMessage.OpenFailed -> context.getString(R.string.translate_msg_open_failed)
                        TranslateMessage.ImportFailed -> context.getString(R.string.translate_msg_import_failed)
                        TranslateMessage.Removed -> context.getString(R.string.translate_msg_removed)
                        TranslateMessage.ModelDownloaded -> context.getString(R.string.translate_msg_model_downloaded)
                        is TranslateMessage.ModelFailed -> context.getString(R.string.translate_msg_model_failed, m.reason ?: "")
                        TranslateMessage.ModelDeleted -> context.getString(R.string.translate_msg_model_deleted)
                        is TranslateMessage.Imported -> context.getString(
                            R.string.translate_msg_imported,
                            context.getString(m.info.type.label()),
                            "%,d".format(m.info.entries),
                        )
                    },
                )
            }
        }
    }

    TranslateScreen(
        state = state,
        snackbar = snackbar,
        onInputChange = viewModel::onInputChange,
        onSubmit = { viewModel.submit() },
        onPaste = { text -> if (text.isNullOrBlank()) viewModel.onClipboardEmpty() else viewModel.submit(text) },
        onSiteClick = viewModel::openSite,
        onEntryClick = viewModel::openEntry,
        onModeChange = viewModel::setMode,
        onImport = viewModel::importDictionary,
        onRemove = viewModel::removeDictionary,
        onDownloadModel = viewModel::downloadModel,
        onDeleteModel = viewModel::deleteModel,
    )
}

@Composable
internal fun TranslateScreen(
    state: TranslateUiState,
    snackbar: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: (String?) -> Unit,
    onSiteClick: (SourceInfo) -> Unit,
    onEntryClick: (HistoryEntry) -> Unit,
    onModeChange: (TranslateMode) -> Unit,
    onImport: (uri: String, type: DictionaryType) -> Unit,
    onRemove: (DictionaryType) -> Unit,
    onDownloadModel: (wifiOnly: Boolean) -> Unit,
    onDeleteModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingType by remember { mutableStateOf<DictionaryType?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = pendingType
        pendingType = null
        if (uri != null && type != null) onImport(uri.toString(), type)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.translate_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                AINovelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.translate_paste_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.translate_paste_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        label = { Text(stringResource(R.string.translate_paste_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        trailingIcon = {
                            if (state.input.isNotEmpty()) {
                                IconButton(onClick = { onInputChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.translate_clear))
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
                            Text(stringResource(R.string.translate_paste_button))
                        }
                        Button(onClick = onSubmit, enabled = !state.isOpening, modifier = Modifier.weight(1f)) {
                            if (state.isOpening) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringResource(R.string.translate_open_button))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.translate_sites_section)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.sources.forEach { source ->
                            AssistChip(onClick = { onSiteClick(source) }, label = { Text(source.hosts.first()) })
                        }
                    }
                    Text(
                        stringResource(R.string.translate_sites_webview_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.translate_translated_section)) }
            if (state.translated.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.translate_translated_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.translated, key = { it.book.id }) { entry ->
                    TranslatedRow(entry = entry, onClick = { onEntryClick(entry) })
                }
            }

            item { SectionTitle(stringResource(R.string.translate_mode_section)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TranslateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            label = { Text(stringResource(mode.label())) },
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.translate_mlkit_section)) }
            item {
                AINovelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.translate_mlkit_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val status = when {
                        state.mlKit.downloading -> stringResource(R.string.translate_mlkit_status_downloading)
                        state.mlKit.downloaded -> stringResource(R.string.translate_mlkit_status_ready)
                        else -> stringResource(R.string.translate_mlkit_status_missing)
                    }
                    Text(status, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.mlKit.downloaded) {
                            OutlinedButton(onClick = onDeleteModel, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.translate_mlkit_delete))
                            }
                        } else {
                            Button(onClick = { onDownloadModel(true) }, enabled = !state.mlKit.downloading, modifier = Modifier.weight(1f)) {
                                if (state.mlKit.downloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(stringResource(R.string.translate_mlkit_download))
                            }
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.translate_dict_section)) }
            item {
                AINovelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.translate_dict_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    DictionaryType.entries.forEachIndexed { index, type ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DictionaryRow(
                            type = type,
                            info = state.dictionaries.firstOrNull { it.type == type },
                            importing = state.importing == type,
                            onImport = {
                                pendingType = type
                                picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
                            },
                            onRemove = { onRemove(type) },
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun DictionaryRow(
    type: DictionaryType,
    info: DictionaryInfo?,
    importing: Boolean,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(type.label()), style = MaterialTheme.typography.titleSmall)
            val detail = when {
                importing -> stringResource(R.string.translate_dict_importing)
                info == null -> stringResource(R.string.translate_dict_missing)
                else -> stringResource(
                    R.string.translate_dict_entries,
                    "%,d".format(info.entries),
                    if (info.builtIn) stringResource(R.string.translate_dict_builtin) else info.displayName,
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (importing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            if (info != null && !info.builtIn) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.translate_dict_remove))
                }
            }
            TextButton(onClick = onImport) {
                Text(stringResource(if (info == null || info.builtIn) R.string.translate_dict_import else R.string.translate_dict_replace))
            }
        }
    }
}

@Composable
private fun TranslatedRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(url = entry.book.coverUrl, refererUrl = entry.book.url, modifier = Modifier.size(width = 44.dp, height = 60.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.lastChapter?.let {
                Text(
                    it.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun TranslateMode.label(): Int = when (this) {
    TranslateMode.CONVERT -> R.string.translate_mode_convert
    TranslateMode.HAN_VIET -> R.string.translate_mode_hanviet
    TranslateMode.MLKIT -> R.string.translate_mode_mlkit
    TranslateMode.RAW -> R.string.translate_mode_raw
}

private fun DictionaryType.label(): Int = when (this) {
    DictionaryType.NAMES -> R.string.translate_dict_names
    DictionaryType.VIETPHRASE -> R.string.translate_dict_vietphrase
    DictionaryType.HANVIET -> R.string.translate_dict_hanviet
    DictionaryType.LUATNHAN -> R.string.translate_dict_luatnhan
}
