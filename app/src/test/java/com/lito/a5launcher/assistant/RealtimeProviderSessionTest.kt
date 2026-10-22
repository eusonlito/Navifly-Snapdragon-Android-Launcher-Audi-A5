package com.lito.a5launcher.assistant

import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeProviderSessionTest {
    @Test
    fun responseWaitsForUserTranscriptBeforeNavigationCanCloseTheTurn() {
        val socket = RecordingWebSocket()
        val listener = RecordingVoiceListener()
        val session = OpenAiRealtimeProvider(socket).connect(
            "test-key", AssistantSessionRequest("es-ES", emptyList()), listener,
        )

        session.finishAudio()
        assertEquals(listOf("input_audio_buffer.commit"), socket.sentTypes)

        socket.receive("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"Llévame a Madrid"}""")
        assertEquals(listOf("user:Llévame a Madrid"), listener.events)
        assertEquals(listOf("input_audio_buffer.commit", "response.create"), socket.sentTypes)

        socket.receive("""{"type":"response.output_item.done","item":{"type":"function_call","name":"navigate_to","arguments":"{\"destination\":\"Madrid\",\"relative_to_current_location\":false}"}}""")
        socket.receive("""{"type":"response.done"}""")
        assertEquals(listOf("user:Llévame a Madrid", "navigate:Madrid", "complete"), listener.events)
        assertEquals(listOf("turn_complete"), socket.closeReasons)
    }

    @Test
    fun cancelledTurnDoesNotCreateAResponseWhenTranscriptionArrivesLate() {
        val socket = RecordingWebSocket()
        val listener = RecordingVoiceListener()
        val session = OpenAiRealtimeProvider(socket).connect(
            "test-key", AssistantSessionRequest("es-ES", emptyList()), listener,
        )
        session.finishAudio()
        session.cancel()
        socket.receive("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"Madrid"}""")
        assertEquals(listOf("input_audio_buffer.commit"), socket.sentTypes)
        assertEquals(emptyList<String>(), listener.events)
    }

    @Test
    fun geminiSendsHistoryBeforeMarkingTheMicrophoneTurnExplicitly() {
        val socket = RecordingWebSocket()
        val listener = RecordingVoiceListener()
        val session = GeminiLiveProvider(socket).connect(
            "test-key",
            AssistantSessionRequest("es-ES", listOf(ConversationTurn(ConversationRole.USER, "Hola"))),
            listener,
        )

        socket.receive("""{"setupComplete":{}}""")
        assertEquals(false, socket.sentMessages[0].getJSONObject("clientContent").getBoolean("turnComplete"))
        assertEquals(true, socket.sentMessages[1].getJSONObject("realtimeInput").has("activityStart"))
        session.sendAudio(byteArrayOf(0, 1))
        assertEquals("audio/pcm;rate=16000", socket.sentMessages[2].getJSONObject("realtimeInput").getJSONObject("audio").getString("mimeType"))
        session.finishAudio()
        val end = socket.sentMessages[3].getJSONObject("realtimeInput")
        assertEquals(true, end.has("activityEnd"))
        assertEquals(false, end.has("audioStreamEnd"))

        socket.receive("""{"toolCall":{"functionCalls":[{"name":"navigate_to","args":{"destination":"Madrid","relative_to_current_location":false}}]}}""")
        assertEquals(listOf("navigate:Madrid"), listener.events)
    }
}

private class RecordingWebSocket : WebSocket, WebSocket.Factory {
    private lateinit var socketRequest: Request
    private lateinit var listener: WebSocketListener
    val sentMessages = mutableListOf<JSONObject>()
    val sentTypes: List<String> get() = sentMessages.map { it.getString("type") }
    val closeReasons = mutableListOf<String?>()

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        socketRequest = request
        this.listener = listener
        return this
    }

    fun receive(text: String) = listener.onMessage(this, text)
    override fun request(): Request = socketRequest
    override fun queueSize(): Long = 0
    override fun send(text: String): Boolean {
        sentMessages += JSONObject(text)
        return true
    }
    override fun send(bytes: ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean {
        closeReasons += reason
        return true
    }
    override fun cancel() = Unit
}

private class RecordingVoiceListener : RealtimeVoiceProvider.Listener {
    val events = mutableListOf<String>()
    override fun onReady() = Unit
    override fun onAudio(pcm16: ByteArray, sampleRate: Int) = Unit
    override fun onUserTranscript(text: String) { events += "user:$text" }
    override fun onTranscriptDelta(text: String) = Unit
    override fun onDestinationSearch(search: DestinationSearch) { events += "navigate:${search.query}" }
    override fun onTurnComplete() { events += "complete" }
    override fun onFailure(failure: ProviderFailure) { events += "failure:${failure.kind}" }
}
