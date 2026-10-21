package vn.esstar.ainovel.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_prefs")

data class TtsPrefs(
    val engineId: String = "system",
    val voiceId: String? = null,
    /** Playback speed applied by the player, 0.5 .. 3.0. */
    val speed: Float = 1.0f,
    /** Pitch applied at synthesis, 0.5 .. 2.0. */
    val pitch: Float = 1.0f,
    val breakAtComma: Boolean = false,
    /** "Tăng cường ổn định âm thanh": synthesize further ahead before playing. */
    val stableMode: Boolean = false,
    /** Sleep timer in minutes; 0 = off. */
    val sleepTimerMinutes: Int = 0,
) {
    val prefetchSegments: Int get() = if (stableMode) 12 else 4

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }
}

@Singleton
class TtsPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val ENGINE = stringPreferencesKey("engine_id")
        val VOICE = stringPreferencesKey("voice_id")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val COMMA = booleanPreferencesKey("break_at_comma")
        val STABLE = booleanPreferencesKey("stable_mode")
        val SLEEP = intPreferencesKey("sleep_timer_minutes")
    }

    val prefs: Flow<TtsPrefs> = context.ttsDataStore.data.map { p ->
        TtsPrefs(
            engineId = p[Keys.ENGINE] ?: "system",
            voiceId = p[Keys.VOICE],
            speed = (p[Keys.SPEED] ?: 1.0f).coerceIn(TtsPrefs.MIN_SPEED, TtsPrefs.MAX_SPEED),
            pitch = (p[Keys.PITCH] ?: 1.0f).coerceIn(TtsPrefs.MIN_PITCH, TtsPrefs.MAX_PITCH),
            breakAtComma = p[Keys.COMMA] ?: false,
            stableMode = p[Keys.STABLE] ?: false,
            sleepTimerMinutes = p[Keys.SLEEP] ?: 0,
        )
    }

    suspend fun current(): TtsPrefs = prefs.first()

    suspend fun setVoice(engineId: String, voiceId: String?) = context.ttsDataStore.edit {
        it[Keys.ENGINE] = engineId
        if (voiceId == null) it.remove(Keys.VOICE) else it[Keys.VOICE] = voiceId
    }

    suspend fun setSpeed(value: Float) = context.ttsDataStore.edit { it[Keys.SPEED] = value.coerceIn(TtsPrefs.MIN_SPEED, TtsPrefs.MAX_SPEED) }
    suspend fun setPitch(value: Float) = context.ttsDataStore.edit { it[Keys.PITCH] = value.coerceIn(TtsPrefs.MIN_PITCH, TtsPrefs.MAX_PITCH) }
    suspend fun setBreakAtComma(value: Boolean) = context.ttsDataStore.edit { it[Keys.COMMA] = value }
    suspend fun setStableMode(value: Boolean) = context.ttsDataStore.edit { it[Keys.STABLE] = value }
    suspend fun setSleepTimerMinutes(value: Int) = context.ttsDataStore.edit { it[Keys.SLEEP] = value.coerceIn(0, 240) }
}
