package vn.esstar.ainovel.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import vn.esstar.ainovel.domain.model.GlossaryEntry
import vn.esstar.ainovel.domain.model.TextToken

/**
 * A converted paragraph whose words know their Chinese source: a tap on a word opens the meaning
 * chooser, a tap on punctuation/space toggles the reader chrome like a plain paragraph.
 */
@Composable
internal fun TokenParagraph(
    tokens: List<TextToken>,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onWordTap: (TextToken) -> Unit,
    onTap: () -> Unit,
    highlightRange: IntRange? = null,
    highlightColor: Color = Color.Transparent,
    onLongPress: () -> Unit = {},
) {
    val plain = remember(tokens) { tokens.joinToString("") { it.text } }
    val text = remember(plain, highlightRange, highlightColor) {
        if (highlightRange == null || highlightRange.isEmpty()) AnnotatedString(plain)
        else buildAnnotatedString {
            append(plain)
            addStyle(SpanStyle(background = highlightColor), highlightRange.first.coerceIn(0, plain.length), (highlightRange.last + 1).coerceIn(0, plain.length))
        }
    }
    // Character offset at which each token starts, for mapping a tap back to its token.
    val starts = remember(tokens) {
        IntArray(tokens.size).also { arr ->
            var pos = 0
            tokens.forEachIndexed { i, t -> arr[i] = pos; pos += t.text.length }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = MaterialTheme.colorScheme.onBackground,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(tokens) {
            detectTapGestures(onLongPress = { onLongPress() }) { position ->
                val result = layout ?: return@detectTapGestures onTap()
                val offset = result.getOffsetForPosition(position)
                val index = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
                val token = tokens.getOrNull(index)
                if (token != null && token.isChinese && offset < starts[index] + token.text.length) onWordTap(token) else onTap()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WordSheet(
    token: TextToken,
    onDismiss: () -> Unit,
    onSave: (target: String, allBooks: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember(token) { mutableStateOf("") }
    var picked by remember(token) { mutableStateOf<String?>(null) }
    val chosen = custom.trim().ifEmpty { picked ?: "" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.reader_word_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(token.source, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.reader_word_current, token.text.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (token.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    token.alternatives.forEach { alt ->
                        FilterChip(selected = picked == alt, onClick = { picked = if (picked == alt) null else alt; custom = "" }, label = { Text(alt) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it; if (it.isNotBlank()) picked = null },
                label = { Text(stringResource(R.string.reader_word_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSave(chosen, true) }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_word_save_all))
                }
                Button(onClick = { onSave(chosen, false) }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_word_save_book))
                }
            }
        }
    }
}

@Composable
internal fun GlossarySheet(entries: List<GlossaryEntry>, onRemove: (GlossaryEntry) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text(stringResource(R.string.reader_glossary_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.reader_glossary_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.source, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                entry.target + if (entry.bookId == null) "  ·  " + stringResource(R.string.reader_glossary_all_books) else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onRemove(entry) }) { Text(stringResource(R.string.reader_glossary_delete)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
