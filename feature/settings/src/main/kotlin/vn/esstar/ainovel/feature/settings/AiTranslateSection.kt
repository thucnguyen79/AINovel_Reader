package vn.esstar.ainovel.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.SectionTitle
import vn.esstar.ainovel.domain.model.LlmCacheInfo
import vn.esstar.ainovel.domain.model.LlmProviderKind
import vn.esstar.ainovel.domain.model.LlmSettings

/** Connection test / model listing state shown under the form. */
sealed interface LlmUiStatus {
    data object Idle : LlmUiStatus
    data object Busy : LlmUiStatus
    data class Ok(val detail: String) : LlmUiStatus
    data class Error(val message: String) : LlmUiStatus
}

/**
 * BYOK card. Fields are edited locally and persisted when the user taps "Lưu" or "Lưu và kiểm tra", so a
 * half-typed key is never saved. The key is masked by default.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiTranslateSection(
    saved: LlmSettings,
    status: LlmUiStatus,
    models: List<String>,
    cache: LlmCacheInfo?,
    prefetch: Int,
    onPrefetch: (Int) -> Unit,
    onSave: (LlmSettings) -> Unit,
    onSaveAndTest: (LlmSettings) -> Unit,
    onFetchModels: (LlmSettings) -> Unit,
    onClearCache: () -> Unit,
) {
    var provider by remember(saved.provider) { mutableStateOf(saved.provider) }
    var apiKey by remember(saved.apiKey) { mutableStateOf(saved.apiKey) }
    var model by remember(saved.model, saved.provider) { mutableStateOf(saved.model) }
    var baseUrl by remember(saved.baseUrl) { mutableStateOf(saved.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    LaunchedEffect(models) { if (models.isNotEmpty()) modelMenu = true }

    val draft = LlmSettings(provider = provider, apiKey = apiKey, model = model, baseUrl = baseUrl)
    val dirty = draft != saved
    val suggestions = (models.ifEmpty { LlmSettings.suggestedModels(provider) })

    SectionTitle(stringResource(R.string.settings_section_ai))
    AINovelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_ai_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LlmProviderKind.entries.forEach { kind ->
                FilterChip(
                    selected = provider == kind,
                    onClick = {
                        if (provider != kind) {
                            provider = kind
                            model = if (kind == saved.provider) saved.model else ""
                        }
                    },
                    label = { Text(stringResource(kind.label())) },
                )
            }
        }
        Text(
            stringResource(provider.help()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.settings_ai_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(if (showKey) R.string.settings_ai_key_hide else R.string.settings_ai_key_show),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (provider == LlmProviderKind.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.settings_ai_base_url)) },
                placeholder = { Text("https://host/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Column {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.settings_ai_model)) },
                placeholder = { Text(LlmSettings.defaultModel(provider).ifBlank { stringResource(R.string.settings_ai_model_required) }) },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { if (suggestions.isNotEmpty()) modelMenu = true else onFetchModels(draft) }) {
                        Text(stringResource(R.string.settings_ai_model_pick))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                suggestions.take(60).forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = { model = id; modelMenu = false })
                }
                if (models.isEmpty() && apiKey.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_ai_model_fetch)) },
                        onClick = { modelMenu = false; onFetchModels(draft) },
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSaveAndTest(draft) }, enabled = status != LlmUiStatus.Busy && draft.isConfigured, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_ai_save_test))
            }
            OutlinedButton(onClick = { onSave(draft) }, enabled = dirty, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_ai_save))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            when (status) {
                LlmUiStatus.Idle -> Text(
                    if (saved.isConfigured) stringResource(R.string.settings_ai_status_ready, saved.effectiveModel) else stringResource(R.string.settings_ai_status_unset),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LlmUiStatus.Busy -> {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_ai_status_testing), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }
                is LlmUiStatus.Ok -> Text(stringResource(R.string.settings_ai_status_ok, status.detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                is LlmUiStatus.Error -> Text(stringResource(R.string.settings_ai_status_error, status.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (cache != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.settings_ai_cache, cache.chapters, formatBytes(cache.bytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearCache, enabled = cache.chapters > 0) { Text(stringResource(R.string.settings_ai_cache_clear)) }
            }
        }
        Text(stringResource(R.string.settings_ai_prefetch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            stringResource(R.string.settings_ai_prefetch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0, 3, 5, 10, 20).forEach { n ->
                FilterChip(
                    selected = prefetch == n,
                    onClick = { onPrefetch(n) },
                    label = { Text(if (n == 0) stringResource(R.string.settings_ai_prefetch_off) else stringResource(R.string.settings_ai_prefetch_n, n)) },
                )
            }
        }
        Text(
            stringResource(R.string.settings_ai_usage_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun LlmProviderKind.label(): Int = when (this) {
    LlmProviderKind.GEMINI -> R.string.settings_ai_provider_gemini
    LlmProviderKind.OPENROUTER -> R.string.settings_ai_provider_openrouter
    LlmProviderKind.GROQ -> R.string.settings_ai_provider_groq
    LlmProviderKind.OPENAI_COMPATIBLE -> R.string.settings_ai_provider_custom
}

private fun LlmProviderKind.help(): Int = when (this) {
    LlmProviderKind.GEMINI -> R.string.settings_ai_help_gemini
    LlmProviderKind.OPENROUTER -> R.string.settings_ai_help_openrouter
    LlmProviderKind.GROQ -> R.string.settings_ai_help_groq
    LlmProviderKind.OPENAI_COMPATIBLE -> R.string.settings_ai_help_custom
}
