package vn.esstar.ainovel.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.esstar.ainovel.core.designsystem.component.AINovelCard
import vn.esstar.ainovel.core.designsystem.component.SectionTitle

@Composable
internal fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        SectionTitle(stringResource(R.string.settings_section_audio))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_placeholder_speed))
            Text(stringResource(R.string.settings_placeholder_pitch))
        }

        SectionTitle(stringResource(R.string.settings_section_voice))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_placeholder_voice))
        }

        SectionTitle(stringResource(R.string.settings_section_system))
        AINovelCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_placeholder_system))
        }
    }
}
