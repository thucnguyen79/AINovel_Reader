package vn.esstar.ainovel.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.esstar.ainovel.domain.model.TranslateMode

private val Context.translateDataStore: DataStore<Preferences> by preferencesDataStore(name = "translate_prefs")

@Singleton
class TranslatePreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private val KEY_MODE = stringPreferencesKey("mode")

    val mode: Flow<TranslateMode> = context.translateDataStore.data.map { p ->
        p[KEY_MODE]?.let { runCatching { TranslateMode.valueOf(it) }.getOrNull() } ?: TranslateMode.CONVERT
    }

    suspend fun setMode(mode: TranslateMode) = context.translateDataStore.edit { it[KEY_MODE] = mode.name }
}
