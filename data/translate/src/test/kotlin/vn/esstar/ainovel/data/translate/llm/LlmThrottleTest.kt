package vn.esstar.ainovel.data.translate.llm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmThrottleTest {

    @Test
    fun `background waits for the interactive window and the cooldown`() = runTest {
        var now = 1_000_000L
        val throttle = LlmThrottle { now }
        throttle.markInteractive()
        assertFalse(throttle.isCoolingDown)
        throttle.onRateLimited(20_000)
        assertTrue(throttle.isCoolingDown)

        now += LlmThrottle.INTERACTIVE_WINDOW_MS + 1
        // Interactive window passed, but the cooldown (20 s from the mark) also passed by now.
        assertFalse(throttle.isCoolingDown)
        throttle.awaitBackgroundSlot() // returns immediately
    }

    @Test
    fun `cooldown is clamped to the allowed range`() {
        var now = 0L
        val throttle = LlmThrottle { now }
        throttle.onRateLimited(1)
        now = LlmThrottle.MIN_COOLDOWN_MS - 1
        assertTrue(throttle.isCoolingDown)
        now = LlmThrottle.MIN_COOLDOWN_MS + 1
        assertFalse(throttle.isCoolingDown)
        throttle.onRateLimited(10 * 60_000L)
        now += LlmThrottle.MAX_COOLDOWN_MS + 1
        assertFalse(throttle.isCoolingDown)
    }

    @Test
    fun `gemini retry delay is read from the error body`() {
        val client = object : BaseLlmClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json, vn.esstar.ainovel.domain.model.LlmSettings(apiKey = "k")) {
            override suspend fun complete(system: String, user: String, temperature: Double) = ""
            override suspend fun listModels() = emptyList<String>()
        }
        assertEquals(42_500L, client.retryDelayFromBody("""{"error":{"details":[{"retryDelay":"42s"}]}}"""))
        assertEquals(13_000L, client.retryDelayFromBody("""{"error":{"message":"Please retry in 12.5s."}}"""))
        assertEquals(null, client.retryDelayFromBody("""{"error":{"message":"nope"}}"""))
    }
}
