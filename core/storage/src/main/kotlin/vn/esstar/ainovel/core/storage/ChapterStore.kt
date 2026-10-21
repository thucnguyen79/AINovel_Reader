package vn.esstar.ainovel.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.esstar.ainovel.core.common.di.AppDispatcher
import vn.esstar.ainovel.core.common.di.Dispatcher

/**
 * Layout of app-private storage. Everything lives under `filesDir`, so no storage permission is needed
 * and the OS removes it on uninstall.
 */
@Singleton
class AppStorage @Inject constructor(@ApplicationContext context: Context) {
    val root: File = context.filesDir
    val chapters: File = File(root, "chapters")
    val audioCache: File = File(context.cacheDir, "audio")
    val models: File = File(root, "models")
    val dictionaries: File = File(root, "dicts")
    val logs: File = File(root, "logs")

    fun relativize(file: File): String = file.relativeTo(root).path.replace(File.separatorChar, '/')
    fun resolve(relativePath: String): File = File(root, relativePath)
}

/** Stores chapter text as one UTF-8 file per chapter: `chapters/<bookId>/<chapterId>.txt`. */
@Singleton
class ChapterStore @Inject constructor(
    private val storage: AppStorage,
    @Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private fun fileFor(bookId: Long, chapterId: Long): File =
        File(File(storage.chapters, bookId.toString()), "$chapterId.txt")

    /** Writes [paragraphs] and returns the relative path to persist in the database. */
    suspend fun write(bookId: Long, chapterId: Long, paragraphs: List<String>): String = withContext(io) {
        val file = fileFor(bookId, chapterId)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(paragraphs.joinToString("\n"), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Cannot write ${file.path}" }
        }
        storage.relativize(file)
    }

    suspend fun read(relativePath: String): List<String>? = withContext(io) {
        val file = storage.resolve(relativePath)
        if (!file.isFile) return@withContext null
        file.readText(Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
    }

    suspend fun delete(relativePath: String) = withContext(io) { storage.resolve(relativePath).delete() }

    suspend fun deleteBook(bookId: Long) = withContext(io) { File(storage.chapters, bookId.toString()).deleteRecursively() }

    suspend fun sizeOfBook(bookId: Long): Long = withContext(io) {
        File(storage.chapters, bookId.toString()).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
