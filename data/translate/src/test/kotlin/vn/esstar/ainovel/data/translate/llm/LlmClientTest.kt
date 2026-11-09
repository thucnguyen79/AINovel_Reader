package vn.esstar.ainovel.data.translate.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import vn.esstar.ainovel.domain.model.LlmException
import vn.esstar.ainovel.domain.model.LlmProviderKind
import vn.esstar.ainovel.domain.model.LlmSettings

class LlmClientTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun base() = server.url("/v1").toString().trimEnd('/')

    private fun openAi(model: String = "test-model") = OpenAiCompatibleClient(
        http, json, LlmSettings(provider = LlmProviderKind.OPENAI_COMPATIBLE, apiKey = "sk-test", model = model, baseUrl = base()),
    )

    private fun response(body: String, code: Int = 200, vararg headers: String) =
        MockResponse.Builder().code(code).body(body).apply { headers.forEach { addHeader(it) } }.build()

    @Test
    fun `openai compatible client sends chat payload with bearer key and reads the reply`() = runTest {
        server.enqueue(response("""{"choices":[{"message":{"role":"assistant","content":"[1] Xin chào"},"finish_reason":"stop"}]}"""))

        val reply = openAi().complete("sys", "user text")

        assertEquals("[1] Xin chào", reply)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer sk-test", request.headers["Authorization"])
        val payload = json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("test-model", payload["model"]!!.jsonPrimitive.content)
        val messages = payload["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user text", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `rate limit is retried after Retry-After and then succeeds`() = runTest {
        server.enqueue(response("""{"error":{"message":"slow down"}}""", 429, "Retry-After: 1"))
        server.enqueue(response("""{"choices":[{"message":{"content":"ok"}}]}"""))

        assertEquals("ok", openAi().complete("s", "u"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `invalid key is reported with a readable message`() = runTest {
        server.enqueue(response("""{"error":{"message":"Invalid API key","type":"auth"}}""", 401))
        try {
            openAi().complete("s", "u")
            fail("expected LlmException")
        } catch (e: LlmException) {
            assertTrue(e.userMessage, e.userMessage.contains("API key"))
            assertTrue(e.userMessage, e.userMessage.contains("Invalid API key"))
        }
    }

    @Test
    fun `gemini client uses generateContent and joins parts`() = runTest {
        server.enqueue(
            response("""{"candidates":[{"content":{"parts":[{"text":"[1] A"},{"text":"\n[2] B"}],"role":"model"},"finishReason":"STOP"}]}"""),
        )
        val settings = LlmSettings(provider = LlmProviderKind.GEMINI, apiKey = "g-key", model = "gemini-test")
        val gemini = GeminiClientForTest(http, json, settings, base())

        val reply = gemini.complete("s", "u")

        assertEquals("[1] A\n[2] B", reply)
        val request = server.takeRequest()
        assertEquals("/v1/models/gemini-test:generateContent", request.url.encodedPath)
        assertEquals("g-key", request.headers["x-goog-api-key"])
        val payload = json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("s", payload["system_instruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(gemini.settings.isConfigured)
    }

    @Test
    fun `gemini invalid key arrives as 400 but is reported as a key problem`() = runTest {
        server.enqueue(response("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""", 400))
        val gemini = GeminiClientForTest(http, json, LlmSettings(provider = LlmProviderKind.GEMINI, apiKey = "bad"), base())
        try {
            gemini.complete("s", "u")
            fail("expected LlmException")
        } catch (e: LlmException) {
            assertTrue(e.userMessage, e.userMessage.startsWith("API key bị từ chối."))
        }
    }

    @Test
    fun `gemini safety block becomes a readable error`() = runTest {
        server.enqueue(response("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        val gemini = GeminiClientForTest(http, json, LlmSettings(provider = LlmProviderKind.GEMINI, apiKey = "k"), base())
        try {
            gemini.complete("s", "u")
            fail("expected LlmException")
        } catch (e: LlmException) {
            assertTrue(e.userMessage, e.userMessage.contains("SAFETY"))
        }
    }

    @Test
    fun `model listings are parsed for both protocols`() = runTest {
        server.enqueue(response("""{"data":[{"id":"b-model"},{"id":"a-model"}]}"""))
        assertEquals(listOf("a-model", "b-model"), openAi().listModels())

        server.enqueue(
            response("""{"models":[{"name":"models/gemini-x","supportedGenerationMethods":["generateContent"]},{"name":"models/embed","supportedGenerationMethods":["embedContent"]}]}"""),
        )
        assertEquals(listOf("gemini-x"), GeminiClientForTest(http, json, LlmSettings(provider = LlmProviderKind.GEMINI, apiKey = "k"), base()).listModels())
    }
}

/** Gemini client whose endpoint is the mock server instead of Google. */
private class GeminiClientForTest(http: OkHttpClient, json: Json, settings: LlmSettings, base: String) : GeminiClient(http, json, settings, base)
