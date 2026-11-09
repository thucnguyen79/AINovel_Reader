package vn.esstar.ainovel.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Body of the "Tóm tắt chương" bottom sheet. */
@Composable
internal fun SummarySheet(state: SummaryState, onAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        Text(stringResource(R.string.reader_ai_summary_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        when (state) {
            SummaryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.reader_ai_summary_loading), modifier = Modifier.padding(start = 12.dp))
            }
            is SummaryState.Failed -> Text(
                stringResource(R.string.reader_ai_summary_error, state.message),
                color = MaterialTheme.colorScheme.error,
            )
            is SummaryState.Ready -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(state.summary.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.reader_ai_summary_model, state.summary.model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (state !is SummaryState.Loading) {
            TextButton(onClick = onAgain, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.reader_ai_summary_again)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
