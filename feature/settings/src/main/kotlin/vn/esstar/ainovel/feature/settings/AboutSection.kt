package vn.esstar.ainovel.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.SectionTitle
import vn.esstar.ainovel.domain.model.AppInfo
import vn.esstar.ainovel.domain.model.AppUpdate

/** Backup / restore card: the user picks where the ZIP goes, the app never touches shared storage itself. */
@Composable
internal fun BackupSection(busy: Boolean, onExport: (String) -> Unit, onImport: (String) -> Unit) {
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onExport(uri.toString())
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri.toString())
    }
    SectionTitle(stringResource(R.string.settings_section_backup))
    AINovelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    create.launch("ainovel-backup-$stamp.zip")
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_export)) }
            OutlinedButton(
                onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_import)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutSection(
    appInfo: AppInfo,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onShareLog: () -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    SectionTitle(stringResource(R.string.settings_section_about))
    AINovelCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_version, appInfo.versionName, appInfo.versionCode), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_about_blurb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        when (update) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Text(stringResource(R.string.settings_update_checking), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            UpdateState.UpToDate -> Text(stringResource(R.string.settings_update_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            is UpdateState.Failed -> Text(stringResource(R.string.settings_update_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            is UpdateState.Available -> UpdateCard(update.update, onOpen = { open(update.update.apkUrl ?: update.update.releaseUrl) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onCheckUpdate, enabled = update != UpdateState.Checking) { Text(stringResource(R.string.settings_update_check)) }
            TextButton(onClick = { open(appInfo.repositoryUrl + "/blob/main/docs/PRIVACY.md") }) { Text(stringResource(R.string.settings_privacy)) }
            TextButton(onClick = { open(appInfo.repositoryUrl) }) { Text(stringResource(R.string.settings_source_code)) }
            TextButton(onClick = onShareLog) { Text(stringResource(R.string.settings_share_log)) }
        }
        Text(
            stringResource(R.string.settings_licenses),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun UpdateCard(update: AppUpdate, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_update_available, update.versionName), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        update.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Button(onClick = onOpen, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.settings_update_download)) }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Failed(val reason: String?) : UpdateState
}
