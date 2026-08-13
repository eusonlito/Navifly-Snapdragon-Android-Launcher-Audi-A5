package com.lito.a5launcher.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

class GeminiLiveProvider(
    private val client: OkHttpClient = defaultRealtimeHttpClient(),
) : RealtimeVoiceProvider {
    override val inputSampleRate: Int = 16_000

    override fun connect(
        apiKey: String,
        request: AssistantSessionRequest,
        listener: RealtimeVoiceProvider.Listener,
    ): RealtimeVoiceProvider.Session {
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val socketRequest = Request.Builder().url(
            "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
                "?key=$encodedKey",
        ).build()
        val bridge = GeminiSocket(listener, request)
        bridge.socket = client.newWebSocket(socketRequest, bridge)
        return bridge
    }

    private class GeminiSocket(
        private val listener: RealtimeVoiceProvider.Listener,
        private val request: AssistantSessionRequest,
    ) : WebSocketListener(), RealtimeVoiceProvider.Session {
        lateinit var socket: WebSocket
        @Volatile private var completed = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(GeminiProtocol.setup(request).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            GeminiProtocol.parse(text).forEach(::dispatch)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            GeminiProtocol.parse(bytes).forEach(::dispatch)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (code != NORMAL_CLOSE_CODE) failIfActive(closeFailure(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val failure = if (response != null) {
                ProviderFailure(
                    ProviderFailureKind.REJECTED_REQUEST,
                    ProtocolJson.providerDiagnostic(
                        runCatching { response.body.string() }.getOrNull(),
                        response.code,
                        response.message,
                    ),
                )
            } else {
                ProviderFailure.CONNECTION
            }
            failIfActive(failure)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code != NORMAL_CLOSE_CODE) failIfActive(closeFailure(code, reason))
        }

        override fun sendAudio(pcm16: ByteArray) {
            val blob = JSONObject()
                .put("mimeType", "audio/pcm;rate=16000")
                .put("data", Base64.getEncoder().encodeToString(pcm16))
            socket.send(
                JSONObject().put(
                    "realtimeInput",
                    JSONObject().put("audio", blob),
                ).toString(),
            )
        }

        override fun finishAudio() {
            socket.send(
                JSONObject().put(
                    "realtimeInput",
                    JSONObject().put("audioStreamEnd", true),
                ).toString(),
            )
        }

        override fun cancel() {
            completed = true
            socket.close(1000, "client_cancelled")
        }

        private fun failIfActive(failure: ProviderFailure) {
            if (completed) return
            completed = true
            listener.onFailure(failure)
        }

        private fun closeFailure(code: Int, reason: String) = ProviderFailure(
            ProviderFailureKind.CONNECTION,
            ProtocolJson.providerDiagnostic(
                payload = runCatching { JSONObject(reason) }.getOrNull(),
                fallbackMessage = "WebSocket $code${reason.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}",
            ),
        )

        private fun dispatch(event: ProtocolEvent) {
            if (event == ProtocolEvent.Ready) {
                GeminiProtocol.initialHistory(request.history)?.let { socket.send(it.toString()) }
                listener.onReady()
                return
            }
            event.dispatchTo(listener) {
                completed = true
                listener.onTurnComplete()
                socket.close(1000, "turn_complete")
            }
        }

        private companion object {
            const val NORMAL_CLOSE_CODE = 1000
        }

    }
}

internal object GeminiProtocol {
    fun parse(bytes: ByteString): List<ProtocolEvent> = parse(bytes.utf8())

    fun initialHistory(history: List<ConversationTurn>): JSONObject? {
        if (history.isEmpty()) return null
        val turns = JSONArray()
        history.forEach { turn ->
            turns.put(
                JSONObject()
                    .put("role", if (turn.role == ConversationRole.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text))),
            )
        }
        return JSONObject().put(
            "clientContent",
            JSONObject().put("turns", turns).put("turnComplete", true),
        )
    }

    fun setup(request: AssistantSessionRequest): JSONObject {
        val declaration = JSONObject()
            .put("name", "navigate_to")
            .put("description", "Navigate to the requested destination without asking the user for coordinates")
            .put("parameters", JSONObject(AssistantPrompt.destinationSearchParametersJson))
        val tools = JSONArray()
            .put(JSONObject().put("googleSearch", JSONObject()))
            .put(JSONObject().put("functionDeclarations", JSONArray().put(declaration)))
        val setup = JSONObject()
            .put("model", "models/${AssistantProvider.GEMINI.model}")
            .put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("AUDIO")),
            )
            .put("systemInstruction", JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", AssistantPrompt.build(request))),
            ))
            .put("tools", tools)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        return JSONObject().put("setup", setup)
    }

    fun parse(text: String): List<ProtocolEvent> = runCatching {
        val root = JSONObject(text)
        if (root.has("setupComplete")) return listOf(ProtocolEvent.Ready)
        root.optJSONObject("error")?.let {
            return listOf(
                ProtocolEvent.Failure(
                    ProviderFailure(
                        ProviderFailureKind.REJECTED_REQUEST,
                        ProtocolJson.providerDiagnostic(root),
                    ),
                ),
            )
        }
        root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            return buildList {
                for (index in 0 until calls.length()) {
                    val call = calls.optJSONObject(index) ?: continue
                    if (call.optString("name") == "navigate_to") {
                        add(
                            ProtocolEvent.DestinationSearchRequested(
                                ProtocolJson.destinationSearch(call.optJSONObject("args") ?: JSONObject()),
                            ),
                        )
                    }
                }
            }
        }
        val content = root.optJSONObject("serverContent") ?: return listOf(ProtocolEvent.Ignored)
        buildList {
            content.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf(String::isNotBlank)?.let { add(ProtocolEvent.UserTranscript(it)) }
            content.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf(String::isNotBlank)?.let { add(ProtocolEvent.Transcript(it)) }
            val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                part.optJSONObject("inlineData")?.let { data ->
                    if (data.optString("mimeType").startsWith("audio/pcm")) {
                        add(ProtocolEvent.Audio(ProtocolJson.decodeAudio(data.optString("data")), 24_000))
                    }
                }
                part.optString("text").takeIf(String::isNotBlank)?.let { add(ProtocolEvent.Transcript(it)) }
            }
            if (content.optBoolean("turnComplete")) add(ProtocolEvent.Complete)
            if (isEmpty()) add(ProtocolEvent.Ignored)
        }
    }.getOrElse { listOf(ProtocolEvent.Failure(ProviderFailure.INVALID_RESPONSE)) }
}
