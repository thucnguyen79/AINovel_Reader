package vn.esstar.ainovel.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import vn.esstar.ainovel.domain.model.AppInfo
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import vn.esstar.ainovel.core.datastore.TtsPrefs
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.SectionTitle
import vn.esstar.ainovel.tts.api.TtsVoice
import vn.esstar.ainovel.tts.api.VoiceModelState
import vn.esstar.ainovel.tts.api.VoiceModelStatus
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import vn.esstar.ainovel.core.datastore.DohMode
import vn.esstar.ainovel.domain.model.StorageInfo

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Exported -> snackbar.showSnackbar(resources.getString(R.string.settings_backup_exported, event.summary.books, event.summary.glossaryEntries))
                is SettingsEvent.Imported -> snackbar.showSnackbar(resources.getString(R.string.settings_backup_imported, event.summary.books, event.summary.glossaryEntries))
                is SettingsEvent.BackupFailed -> snackbar.showSnackbar(resources.getString(R.string.settings_backup_failed, event.reason ?: ""))
                SettingsEvent.NoLog -> snackbar.showSnackbar(resources.getString(R.string.settings_share_log_empty))
                is SettingsEvent.ShareLog -> {
                    val authority = context.packageName + ".files"
                    val uris = ArrayList(event.files.map { FileProvider.getUriForFile(context, authority, it) })
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/plain"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.settings_share_log_subject, viewModel.appInfo.versionName))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, resources.getString(R.string.settings_share_log))) }
                }
            }
        }
    }
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val neuralModels by viewModel.neuralModels.collectAsStateWithLifecycle()
    val dohMode by viewModel.dohMode.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    SettingsScreen(
        prefs = prefs,
        voices = voices,
        engineAvailable = engineAvailable,
        neuralModels = neuralModels,
        dohMode = dohMode,
        storage = storage,
        onDownloadModel = viewModel::downloadModel,
        onCancelModel = viewModel::cancelModel,
        onDeleteModel = viewModel::deleteModel,
        onUseModel = viewModel::useModel,
        onVoice = viewModel::setVoice,
        onSpeed = viewModel::setSpeed,
        onPitch = viewModel::setPitch,
        onBreakAtComma = viewModel::setBreakAtComma,
        onStableMode = viewModel::setStableMode,
        onDohMode = viewModel::setDohMode,
        onClearCaches = viewModel::clearCaches,
        snackbar = snackbar,
        backupBusy = backupBusy,
        onExport = viewModel::exportBackup,
        onImport = viewModel::importBackup,
        appInfo = viewModel.appInfo,
        update = update,
        onCheckUpdate = viewModel::checkUpdate,
        onShareLog = viewModel::shareLog,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    prefs: TtsPrefs,
    voices: List<TtsVoice>,
    engineAvailable: Boolean,
    neuralModels: List<VoiceModelStatus>,
    dohMode: DohMode,
    storage: StorageInfo?,
    onDownloadModel: (String) -> Unit,
    onCancelModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onUseModel: (String) -> Unit,
    onVoice: (TtsVoice?) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onBreakAtComma: (Boolean) -> Unit,
    onStableMode: (Boolean) -> Unit,
    onDohMode: (DohMode) -> Unit,
    onClearCaches: () -> Unit,
    snackbar: SnackbarHostState,
    backupBusy: Boolean,
    onExport: (String) -> Unit,
    onImport: (String) -> Unit,
    appInfo: AppInfo,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SectionTitle(stringResource(R.string.settings_section_audio))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            LabeledSlider(
                label = stringResource(R.string.settings_speed, formatFloat(prefs.speed)),
                value = prefs.speed,
                range = TtsPrefs.MIN_SPEED..TtsPrefs.MAX_SPEED,
                steps = 9,
                onCommit = onSpeed,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LabeledSlider(
                label = stringResource(R.string.settings_pitch, formatFloat(prefs.pitch)),
                value = prefs.pitch,
                range = TtsPrefs.MIN_PITCH..TtsPrefs.MAX_PITCH,
                steps = 5,
                onCommit = onPitch,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_voice))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            if (!engineAvailable) {
                Text(stringResource(R.string.settings_voice_unavailable), color = MaterialTheme.colorScheme.error)
            } else {
                VoiceDropdown(voices = voices, selectedId = prefs.voiceId, onVoice = onVoice)
                Text(
                    stringResource(R.string.settings_voice_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_section_neural))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_neural_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            neuralModels.forEach { status ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NeuralModelRow(
                    status = status,
                    selected = prefs.voiceId == status.voiceId,
                    onDownload = { onDownloadModel(status.model.id) },
                    onCancel = { onCancelModel(status.model.id) },
                    onDelete = { onDeleteModel(status.model.id) },
                    onUse = { onUseModel(status.model.id) },
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_section_system))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                title = stringResource(R.string.settings_break_comma),
                checked = prefs.breakAtComma,
                onChecked = onBreakAtComma,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SwitchRow(
                title = stringResource(R.string.settings_stable_mode),
                subtitle = stringResource(R.string.settings_stable_mode_hint),
                checked = prefs.stableMode,
                onChecked = onStableMode,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_network))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_doh), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_doh_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DohMode.entries.forEach { mode ->
                    FilterChip(
                        selected = dohMode == mode,
                        onClick = { onDohMode(mode) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        DohMode.SYSTEM -> R.string.settings_doh_system
                                        DohMode.GOOGLE -> R.string.settings_doh_google
                                        DohMode.CLOUDFLARE -> R.string.settings_doh_cloudflare
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_storage))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    R.string.settings_storage_summary,
                    formatBytes(storage?.chapterBytes ?: 0),
                    formatBytes(storage?.audioCacheBytes ?: 0),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onClearCaches, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_clear_cache))
            }
            Text(
                stringResource(R.string.settings_clear_cache_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        BackupSection(busy = backupBusy, onExport = onExport, onImport = onImport)

        AboutSection(appInfo = appInfo, update = update, onCheckUpdate = onCheckUpdate, onShareLog = onShareLog)
        Spacer(Modifier.height(8.dp))
    }
    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun NeuralModelRow(
    status: VoiceModelStatus,
    selected: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(status.model.displayName, style = MaterialTheme.typography.titleSmall)
        Text(
            status.model.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val detail = when (status.state) {
            VoiceModelState.NOT_DOWNLOADED -> stringResource(R.string.settings_neural_size, formatBytes(status.model.sizeBytes))
            VoiceModelState.DOWNLOADING -> stringResource(R.string.settings_neural_downloading, (status.progress * 100).toInt())
            VoiceModelState.READY -> if (selected) stringResource(R.string.settings_neural_in_use) else stringResource(R.string.settings_neural_ready)
            VoiceModelState.FAILED -> stringResource(R.string.settings_neural_failed, status.error ?: "")
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (status.state == VoiceModelState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (status.state == VoiceModelState.DOWNLOADING) {
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            when (status.state) {
                VoiceModelState.NOT_DOWNLOADED, VoiceModelState.FAILED -> TextButton(onClick = onDownload) { Text(stringResource(R.string.settings_neural_download)) }
                VoiceModelState.DOWNLOADING -> TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_neural_cancel)) }
                VoiceModelState.READY -> {
                    if (!selected) TextButton(onClick = onUse) { Text(stringResource(R.string.settings_neural_use)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.settings_neural_delete)) }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(voices: List<TtsVoice>, selectedId: String?, onVoice: (TtsVoice?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.id == selectedId }
    val label = selected?.displayName ?: stringResource(R.string.settings_voice_default)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_voice_pick)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_voice_default)) },
                onClick = { expanded = false; onVoice(null) },
            )
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.displayName) },
                    onClick = { expanded = false; onVoice(voice) },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Text(label, style = MaterialTheme.typography.titleMedium)
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local) },
        valueRange = range,
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun formatFloat(v: Float): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
