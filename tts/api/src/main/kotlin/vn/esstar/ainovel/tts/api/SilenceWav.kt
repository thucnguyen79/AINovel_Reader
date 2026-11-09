package vn.esstar.ainovel.tts.api

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a PCM 16-bit mono WAV file containing only silence, used for pauses between chapters. */
object SilenceWav {

    const val DEFAULT_SAMPLE_RATE = 16_000

    fun write(file: File, millis: Int, sampleRate: Int = DEFAULT_SAMPLE_RATE): File {
        require(millis >= 0) { "millis must be >= 0" }
        val dataBytes = (sampleRate.toLong() * millis / 1000L).toInt() * 2
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.write(header(dataBytes, sampleRate))
            val zeros = ByteArray(minOf(dataBytes, 64 * 1024))
            var remaining = dataBytes
            while (remaining > 0) {
                val n = minOf(remaining, zeros.size)
                out.write(zeros, 0, n)
                remaining -= n
            }
        }
        return file
    }

    private fun header(dataBytes: Int, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
    }
}
