package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import okio.ByteString.Companion.encodeUtf8

class RealtimeProtocolTest {
    @Test
    fun openAiUsesCurrentGaRealtimeHandshake() {
        val request = openAiSocketRequest("test-key")
        assertEquals("gpt-realtime-2.1-mini", request.url.queryParameter("model"))
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertEquals(null, request.header("OpenAI-Beta"))

        val session = OpenAiProtocol.sessionUpdate(
            AssistantSessionRequest("es-ES", emptyList(), null),
        ).getJSONObject("session")
        val audio = session.getJSONObject("audio")
        assertEquals(24_000, audio.getJSONObject("input").getJSONObject("format").getInt("rate"))
        assertEquals(24_000, audio.getJSONObject("output").getJSONObject("format").getInt("rate"))
    }

    @Test
    fun connectionTestsUseLowCostTextModelsWithoutKeysInUrls() {
        val tester = ProviderConnectionTester()
        val openAi = tester.buildRequest(AssistantProvider.OPENAI, "openai-secret")
        assertEquals("gpt-5.4-nano", JSONObject(openAi.bodyUtf8()).getString("model"))
        assertEquals("Bearer openai-secret", openAi.header("Authorization"))
        assertTrue(!openAi.url.toString().contains("openai-secret"))

        val gemini = tester.buildRequest(AssistantProvider.GEMINI, "gemini-secret")
        assertTrue(gemini.url.toString().contains("gemini-3.5-flash-lite:generateContent"))
        assertEquals("gemini-secret", gemini.header("x-goog-api-key"))
        assertTrue(!gemini.url.toString().contains("gemini-secret"))
    }

    @Test
    fun openAiParsesNavigationFunction() {
        val event = OpenAiProtocol.parse(
            """{"type":"response.output_item.done","item":{"type":"function_call","name":"navigate_to","call_id":"call-1","arguments":"{\"destination\":\"centro de Ordes\",\"relative_to_current_location\":false}"}}""",
        )
        assertTrue(event is ProtocolEvent.DestinationSearchRequested)
        val search = event as ProtocolEvent.DestinationSearchRequested
        assertEquals("centro de Ordes", search.search.query)
        assertEquals(DestinationSearchMode.RELEVANCE, search.search.mode)
    }

    @Test
    fun geminiProcessesEveryAudioPartAndTurnCompletion() {
        val events = GeminiProtocol.parse(
            """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAE="}},{"text":"Hola"}]},"turnComplete":true}}""",
        )
        assertTrue(events.any { it is ProtocolEvent.Audio })
        assertTrue(events.any { it is ProtocolEvent.Transcript })
        assertTrue(events.any { it is ProtocolEvent.Complete })
    }

    @Test
    fun geminiProcessesBinarySetupConfirmation() {
        val events = GeminiProtocol.parse("""{"setupComplete":{}}""".encodeUtf8())
        assertEquals(listOf(ProtocolEvent.Ready), events)
    }

    @Test
    fun geminiSetupIncludesSearchAndNavigationTool() {
        val setup = GeminiProtocol.setup(AssistantSessionRequest("es-ES", emptyList(), null))
            .getJSONObject("setup")
        assertTrue(!setup.has("responseModalities"))
        assertEquals(
            "AUDIO",
            setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0),
        )
        val tools = setup.getJSONArray("tools")
        assertTrue(tools.getJSONObject(0).has("googleSearch"))
        assertTrue(tools.getJSONObject(1).has("functionDeclarations"))
        assertEquals(
            "navigate_to",
            tools.getJSONObject(1).getJSONArray("functionDeclarations").getJSONObject(0).getString("name"),
        )
    }

    @Test
    fun providerErrorsPreserveSafeDiagnostics() {
        val openAiFailure = (OpenAiProtocol.parse(
            """{"type":"error","error":{"message":"Invalid key","type":"invalid_request_error","code":"invalid_api_key"}}""",
        ) as ProtocolEvent.Failure).failure
        assertEquals(ProviderFailureKind.REJECTED_REQUEST, openAiFailure.kind)
        assertEquals("invalid_api_key / invalid_request_error", openAiFailure.diagnostic?.code)
        assertEquals("Invalid key", openAiFailure.diagnostic?.message)

        val geminiFailure = (GeminiProtocol.parse(
            """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""",
        ).single() as ProtocolEvent.Failure).failure
        assertEquals(ProviderFailureKind.REJECTED_REQUEST, geminiFailure.kind)
        assertEquals("INVALID_ARGUMENT / 400", geminiFailure.diagnostic?.code)
        assertEquals("API key not valid", geminiFailure.diagnostic?.message)

        assertEquals(
            ProviderFailure.INVALID_RESPONSE,
            (OpenAiProtocol.parse("{") as ProtocolEvent.Failure).failure,
        )
        assertEquals(
            ProviderFailure.INVALID_RESPONSE,
            (GeminiProtocol.parse("{").single() as ProtocolEvent.Failure).failure,
        )
    }

    @Test
    fun providerDiagnosticsRedactCredentialValues() {
        val diagnostic = ProtocolJson.providerDiagnostic(
            """{"error":{"message":"API key=AIza-secret rejected","status":"PERMISSION_DENIED"}}""",
            403,
            "Forbidden",
        )
        assertEquals("HTTP 403 · PERMISSION_DENIED · API key=•••• rejected", diagnostic.displayText())
        assertTrue(!diagnostic.displayText().contains("AIza-secret"))

        val maskedProviderKey = ProtocolJson.providerDiagnostic(
            """{"error":{"message":"Incorrect API key provided: sk-proj-************************7egA. Check your key.","code":"invalid_api_key"}}""",
            401,
            "Unauthorized",
        )
        assertEquals(
            "HTTP 401 · invalid_api_key · Incorrect API key provided: •••• Check your key.",
            maskedProviderKey.displayText(),
        )
        assertTrue(!maskedProviderKey.displayText().contains("7egA"))
    }

    @Test
    fun conversationHistoryKeepsNativeRoles() {
        val history = listOf(
            ConversationTurn(ConversationRole.USER, "Busca Ordes"),
            ConversationTurn(ConversationRole.ASSISTANT, "He encontrado Ordes"),
        )
        val openAiItems = OpenAiProtocol.historyEvents(history)
        assertEquals("user", openAiItems[0].getJSONObject("item").getString("role"))
        assertEquals("assistant", openAiItems[1].getJSONObject("item").getString("role"))

        val gemini = GeminiProtocol.initialHistory(history)!!.getJSONObject("clientContent")
        assertEquals("user", gemini.getJSONArray("turns").getJSONObject(0).getString("role"))
        assertEquals("model", gemini.getJSONArray("turns").getJSONObject(1).getString("role"))
        assertTrue(gemini.getBoolean("turnComplete"))

        val request = AssistantSessionRequest("es-ES", history, null)
        assertTrue(!AssistantPrompt.build(request).contains("Busca Ordes"))
        assertTrue(!GeminiProtocol.setup(request).getJSONObject("setup").has("historyConfig"))
    }
}

private fun okhttp3.Request.bodyUtf8(): String {
    val buffer = okio.Buffer()
    body!!.writeTo(buffer)
    return buffer.readUtf8()
}
