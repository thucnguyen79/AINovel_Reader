package vn.esstar.ainovel.core.common.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Writes INFO+ logs to `app.log` in [dir] so users can share them when reporting a problem.
 * Rotates once at [maxBytes] (keeps one previous file). No personal data is logged on purpose:
 * URLs of chapters are fine, text content is never logged.
 */
class FileLoggingTree(private val dir: File, private val maxBytes: Long = 512 * 1024) : Timber.Tree() {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "file-log").apply { isDaemon = true } }
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    val file: File get() = File(dir, "app.log")

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = buildString {
            append(format.format(Date())).append(' ')
            append(when (priority) { Log.INFO -> "I"; Log.WARN -> "W"; Log.ERROR -> "E"; Log.ASSERT -> "A"; else -> "D" })
            append('/').append(tag ?: "App").append(": ").append(message)
            if (t != null) append('\n').append(Log.getStackTraceString(t))
            append('\n')
        }
        executor.execute {
            runCatching {
                dir.mkdirs()
                val f = file
                if (f.length() > maxBytes) {
                    val old = File(dir, "app.log.1")
                    old.delete()
                    f.renameTo(old)
                }
                f.appendText(line)
            }
        }
    }

    /** The current log followed by the previous one, newest last; empty when nothing was logged yet. */
    fun snapshotFiles(): List<File> = listOf(File(dir, "app.log.1"), file).filter { it.isFile && it.length() > 0 }
}
