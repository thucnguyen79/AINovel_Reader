package vn.esstar.ainovel.tts.api

import java.io.File
import java.security.MessageDigest

/**
 * File cache for synthesized segments keyed by engine + voice params + text.
 * Size-bounded LRU by last-modified time; eviction runs opportunistically after writes.
 */
class AudioCache(
    private val dir: File,
    private val maxBytes: Long = 500L * 1024 * 1024,
) {
    init {
        dir.mkdirs()
    }

    fun key(engineId: String, params: VoiceParams, text: String): String =
        sha1("$engineId|${params.cacheTag()}|$text")

    fun fileFor(key: String, extension: String = "wav"): File = File(dir, "$key.$extension")

    fun existing(key: String): File? = listOf("wav", "mp3", "ogg").map { fileFor(key, it) }.firstOrNull { it.isFile && it.length() > 44 }
        ?.also { it.setLastModified(System.currentTimeMillis()) }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Deletes oldest files until the cache fits in [maxBytes]. Cheap enough to call after every write. */
    fun evictIfNeeded() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            total -= f.length()
            f.delete()
            if (total <= maxBytes) break
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
