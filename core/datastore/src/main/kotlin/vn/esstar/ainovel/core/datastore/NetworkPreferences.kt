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

private val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(name = "network_prefs")

/** DNS resolver used for story sites. DoH helps when an ISP blocks or slows a site by DNS. */
enum class DohMode { SYSTEM, GOOGLE, CLOUDFLARE }

@Singleton
class NetworkPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private val KEY_DOH = stringPreferencesKey("doh_mode")

    val dohMode: Flow<DohMode> = context.networkDataStore.data.map { p ->
        p[KEY_DOH]?.let { runCatching { DohMode.valueOf(it) }.getOrNull() } ?: DohMode.SYSTEM
    }

    suspend fun setDohMode(mode: DohMode) = context.networkDataStore.edit { it[KEY_DOH] = mode.name }
}
