package vn.esstar.ainovel.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vn.esstar.ainovel.domain.model.LlmProviderKind
import vn.esstar.ainovel.domain.model.LlmSettings

private val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_prefs")

/**
 * BYOK settings and the set of books in AI mode. The key lives in app-private storage (no backup):
 * it is the user's own key, never shipped with the app, and is excluded from the ZIP backup on purpose.
 */
@Singleton
class LlmPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val AI_BOOKS = stringSetPreferencesKey("ai_books")
    }

    val settings: Flow<LlmSettings> = context.llmDataStore.data.map { p ->
        LlmSettings(
            provider = p[Keys.PROVIDER]?.let { runCatching { LlmProviderKind.valueOf(it) }.getOrNull() } ?: LlmProviderKind.GEMINI,
            apiKey = p[Keys.API_KEY].orEmpty(),
            model = p[Keys.MODEL].orEmpty(),
            baseUrl = p[Keys.BASE_URL].orEmpty(),
        )
    }

    suspend fun save(settings: LlmSettings) = context.llmDataStore.edit {
        it[Keys.PROVIDER] = settings.provider.name
        it[Keys.API_KEY] = settings.apiKey.trim()
        it[Keys.MODEL] = settings.model.trim()
        it[Keys.BASE_URL] = settings.baseUrl.trim()
    }

    val aiBooks: Flow<Set<Long>> = context.llmDataStore.data.map { p ->
        p[Keys.AI_BOOKS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun isAiBook(bookId: Long): Boolean = aiBooks.first().contains(bookId)

    suspend fun setAiBook(bookId: Long, enabled: Boolean) = context.llmDataStore.edit {
        val current = it[Keys.AI_BOOKS].orEmpty()
        it[Keys.AI_BOOKS] = if (enabled) current + bookId.toString() else current - bookId.toString()
    }
}
