package vn.esstar.ainovel.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

enum class ReaderThemeMode { SYSTEM, LIGHT, DARK, SEPIA, BLACK }

data class ReaderPrefs(
    val fontSizeSp: Int = 18,
    val lineHeight: Float = 1.6f,
    val theme: ReaderThemeMode = ReaderThemeMode.SYSTEM,
) {
    companion object {
        const val MIN_FONT = 12
        const val MAX_FONT = 32
    }
}

@Singleton
class ReaderPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("theme")
    }

    val prefs: Flow<ReaderPrefs> = context.readerDataStore.data.map { p ->
        ReaderPrefs(
            fontSizeSp = (p[Keys.FONT_SIZE] ?: 18).coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT),
            lineHeight = p[Keys.LINE_HEIGHT] ?: 1.6f,
            theme = p[Keys.THEME]?.let { runCatching { ReaderThemeMode.valueOf(it) }.getOrNull() } ?: ReaderThemeMode.SYSTEM,
        )
    }

    suspend fun setFontSize(sp: Int) {
        context.readerDataStore.edit { it[Keys.FONT_SIZE] = sp.coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT) }
    }

    suspend fun setLineHeight(value: Float) {
        context.readerDataStore.edit { it[Keys.LINE_HEIGHT] = value.coerceIn(1.0f, 2.4f) }
    }

    suspend fun setTheme(theme: ReaderThemeMode) {
        context.readerDataStore.edit { it[Keys.THEME] = theme.name }
    }
}
