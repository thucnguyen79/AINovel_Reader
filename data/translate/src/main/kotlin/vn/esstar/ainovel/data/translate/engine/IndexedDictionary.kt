package vn.esstar.ainovel.data.translate.engine

import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Read-only dictionary stored as a sorted binary file and queried through a memory-mapped buffer, so a
 * one-million-entry VietPhrase costs page cache rather than heap.
 *
 * File layout (big-endian): magic "AIDC", version, entryCount, maxKeyLength, offsets[entryCount] (absolute),
 * then records of `u16 keyLen, key(utf-8), u16 valueLen, value(utf-8)`. Keys are sorted by unsigned UTF-8 bytes.
 */
class IndexedDictionary private constructor(
    private val channel: FileChannel,
    private val buf: ByteBuffer,
    val size: Int,
    val maxKeyLength: Int,
    private val offsetsStart: Int,
) : Closeable {

    /** Exact lookup. */
    fun lookup(key: String): String? {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = compareKeyAt(mid, keyBytes)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return valueAt(mid)
            }
        }
        return null
    }

    /** Longest key that is a prefix of [text] starting at [start]; returns (length in chars, value). */
    fun longestMatch(text: CharSequence, start: Int, maxLen: Int = maxKeyLength): Pair<Int, String>? {
        val limit = minOf(maxLen, text.length - start)
        for (len in limit downTo 1) {
            val value = lookup(text.subSequence(start, start + len).toString()) ?: continue
            return len to value
        }
        return null
    }

    private fun recordOffset(index: Int): Int = buf.getInt(offsetsStart + index * 4)

    private fun compareKeyAt(index: Int, key: ByteArray): Int {
        var pos = recordOffset(index)
        val len = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2
        val n = minOf(len, key.size)
        for (i in 0 until n) {
            val a = buf.get(pos + i).toInt() and 0xFF
            val b = key[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return len - key.size
    }

    private fun valueAt(index: Int): String {
        var pos = recordOffset(index)
        val keyLen = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2 + keyLen
        val valLen = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2
        val bytes = ByteArray(valLen)
        for (i in 0 until valLen) bytes[i] = buf.get(pos + i)
        return String(bytes, Charsets.UTF_8)
    }

    override fun close() {
        channel.close()
    }

    companion object {
        private const val MAGIC = 0x41494443 // "AIDC"
        private const val VERSION = 1
        private const val HEADER_BYTES = 16

        fun open(file: File): IndexedDictionary {
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            require(buf.getInt(0) == MAGIC) { "Not a dictionary file: ${file.name}" }
            require(buf.getInt(4) == VERSION) { "Unsupported dictionary version" }
            val count = buf.getInt(8)
            val maxKey = buf.getInt(12)
            return IndexedDictionary(channel, buf, count, maxKey, HEADER_BYTES)
        }

        /**
         * Builds a dictionary file from `key=value` entries (later duplicates win). Returns the entry count.
         * Keys longer than 65535 bytes or values longer than 65535 bytes are skipped.
         */
        fun build(entries: Sequence<Pair<String, String>>, target: File): Int {
            val map = LinkedHashMap<String, String>()
            for ((k, v) in entries) {
                val key = k.trim()
                val value = v.trim()
                if (key.isEmpty() || value.isEmpty()) continue
                map[key] = value
            }
            val encoded = map.entries.map { (k, v) -> k.toByteArray(Charsets.UTF_8) to v.toByteArray(Charsets.UTF_8) }
                .filter { (k, v) -> k.size <= 0xFFFF && v.size <= 0xFFFF }
                .sortedWith { a, b -> compareUnsigned(a.first, b.first) }
            val maxKeyChars = map.keys.maxOfOrNull { it.length } ?: 0

            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeInt(encoded.size)
                out.writeInt(maxKeyChars)
                var offset = HEADER_BYTES + encoded.size * 4
                for ((k, v) in encoded) {
                    out.writeInt(offset)
                    offset += 2 + k.size + 2 + v.size
                }
                for ((k, v) in encoded) {
                    out.writeShort(k.size)
                    out.write(k)
                    out.writeShort(v.size)
                    out.write(v)
                }
            }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Cannot write ${target.path}" }
            return encoded.size
        }

        private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a[i].toInt() and 0xFF
                val y = b[i].toInt() and 0xFF
                if (x != y) return x - y
            }
            return a.size - b.size
        }
    }
}

/** Parses QuickTranslator-style `key=value` text (UTF-8, optional BOM, `#` comments). */
object DictionaryTextParser {
    fun parse(lines: Sequence<String>): Sequence<Pair<String, String>> = lines
        .map { it.removePrefix("\uFEFF").trimEnd('\r') }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) null else line.substring(0, eq) to line.substring(eq + 1)
        }
}
