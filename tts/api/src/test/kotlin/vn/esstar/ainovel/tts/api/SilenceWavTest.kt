package vn.esstar.ainovel.tts.api

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceWavTest {

    @Test
    fun `writes a valid pcm header and the right amount of silence`() {
        val file = File.createTempFile("silence", ".wav").apply { deleteOnExit() }
        SilenceWav.write(file, millis = 3000, sampleRate = 16_000)

        val bytes = file.readBytes()
        assertEquals(44 + 96_000, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 96_000, buf.getInt(4))
        assertEquals(1, buf.getShort(20).toInt()) // PCM
        assertEquals(1, buf.getShort(22).toInt()) // mono
        assertEquals(16_000, buf.getInt(24))
        assertEquals(16, buf.getShort(34).toInt())
        assertEquals(96_000, buf.getInt(40))
        assertTrue(bytes.drop(44).all { it == 0.toByte() })
    }

    @Test
    fun `zero duration still yields a playable header`() {
        val file = File.createTempFile("silence0", ".wav").apply { deleteOnExit() }
        SilenceWav.write(file, millis = 0)
        assertEquals(44, file.length())
    }
}
