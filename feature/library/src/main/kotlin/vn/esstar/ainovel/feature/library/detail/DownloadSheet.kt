package vn.esstar.ainovel.feature.library.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.esstar.ainovel.feature.library.R

private val COUNT_OPTIONS = listOf(30, 100, 300)

/**
 * Lets the user pick a chapter range to download. Unlike the reference app we do not cap a request at
 * 30 chapters: the worker paces requests and backs off when the site throttles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadSheet(
    bookTitle: String,
    chapterCount: Int,
    continueIndex: Int?,
    onDownload: (fromIndex: Int, count: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromContinue by rememberSaveable { mutableStateOf(continueIndex != null) }
    var selectedCount by rememberSaveable { mutableIntStateOf(30) }
    val fromIndex = if (fromContinue && continueIndex != null) continueIndex else 0
    val remaining = (chapterCount - fromIndex).coerceAtLeast(0)
    val count = if (selectedCount == 0) remaining else minOf(selectedCount, remaining)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp).fillMaxWidth(),
        )
        Text(stringResource(R.string.download_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            bookTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.download_from), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = !fromContinue,
                onClick = { fromContinue = false },
                label = { Text(stringResource(R.string.download_from_start)) },
            )
            if (continueIndex != null) {
                FilterChip(
                    selected = fromContinue,
                    onClick = { fromContinue = true },
                    label = { Text(stringResource(R.string.download_from_current, continueIndex + 1)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.download_count), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            COUNT_OPTIONS.forEach { n ->
                FilterChip(selected = selectedCount == n, onClick = { selectedCount = n }, label = { Text(n.toString()) })
            }
            FilterChip(
                selected = selectedCount == 0,
                onClick = { selectedCount = 0 },
                label = { Text(stringResource(R.string.download_all_remaining, remaining)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.download_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onDownload(fromIndex, count) },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.download_confirm, count))
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.download_cancel))
        }
        Spacer(Modifier.height(24.dp))
    }
}
