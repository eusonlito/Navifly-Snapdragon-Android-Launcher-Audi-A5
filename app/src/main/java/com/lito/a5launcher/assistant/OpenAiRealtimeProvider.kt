package com.lito.a5launcher.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.Base64

class OpenAiRealtimeProvider(
    private val client: OkHttpClient = defaultRealtimeHttpClient(),
) : RealtimeVoiceProvider {
    override val inputSampleRate: Int = 24_000

    override fun connect(
        apiKey: String,
        request: AssistantSessionRequest,
        listener: RealtimeVoiceProvider.Listener,
    ): RealtimeVoiceProvider.Session {
        val socketRequest = openAiSocketRequest(apiKey)
        val bridge = OpenAiSocket(listener, request)
        bridge.socket = client.newWebSocket(socketRequest, bridge)
        return bridge
    }

    private class OpenAiSocket(
        private val listener: RealtimeVoiceProvider.Listener,
        private val request: AssistantSessionRequest,
    ) : WebSocketListener(), RealtimeVoiceProvider.Session {
        lateinit var socket: WebSocket
        @Volatile private var completed = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(OpenAiProtocol.sessionUpdate(request).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            dispatch(OpenAiProtocol.parse(text))
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
            failIfActive(
                ProviderFailure(
                    ProviderFailureKind.CONNECTION,
                    ProtocolJson.providerDiagnostic(
                        payload = runCatching { JSONObject(reason) }.getOrNull(),
                        fallbackMessage = "WebSocket $code${reason.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}",
                    ),
                ),
            )
        }

        override fun sendAudio(pcm16: ByteArray) {
            socket.send(
                JSONObject()
                    .put("type", "input_audio_buffer.append")
                    .put("audio", Base64.getEncoder().encodeToString(pcm16))
                    .toString(),
            )
        }

        override fun finishAudio() {
            socket.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
            socket.send(JSONObject().put("type", "response.create").toString())
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

        private fun dispatch(event: ProtocolEvent) {
            if (event == ProtocolEvent.Ready) {
                OpenAiProtocol.historyEvents(request.history).forEach { socket.send(it.toString()) }
                listener.onReady()
                return
            }
            event.dispatchTo(listener) {
                completed = true
                listener.onTurnComplete()
                socket.close(1000, "turn_complete")
            }
        }
    }
}

internal fun openAiSocketRequest(apiKey: String): Request = Request.Builder()
    .url("wss://api.openai.com/v1/realtime?model=${AssistantProvider.OPENAI.model}")
    .header("Authorization", "Bearer $apiKey")
    .build()

internal object OpenAiProtocol {
    fun historyEvents(history: List<ConversationTurn>): List<JSONObject> = history.map { turn ->
        val role = turn.role.promptValue
        val contentType = if (turn.role == ConversationRole.USER) "input_text" else "output_text"
        JSONObject()
            .put("type", "conversation.item.create")
            .put(
                "item",
                JSONObject()
                    .put("type", "message")
                    .put("role", role)
                    .put("content", JSONArray().put(JSONObject().put("type", contentType).put("text", turn.text))),
            )
    }

    fun sessionUpdate(request: AssistantSessionRequest): JSONObject {
        val tool = JSONObject()
            .put("type", "function")
            .put("name", "navigate_to")
            .put("description", "Navigate to the requested destination without asking the user for coordinates")
            .put("parameters", JSONObject(AssistantPrompt.destinationSearchParametersJson))
        val session = JSONObject()
            .put("type", "realtime")
            .put("model", AssistantProvider.OPENAI.model)
            .put("output_modalities", JSONArray().put("audio"))
            .put("instructions", AssistantPrompt.build(request))
            .put(
                "audio",
                JSONObject()
                    .put(
                        "input",
                        JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                            .put("transcription", JSONObject().put("model", "gpt-realtime-whisper"))
                            .put("turn_detection", JSONObject.NULL),
                    )
                    .put(
                        "output",
                        JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                            .put("voice", "marin"),
                    ),
            )
            .put("tools", JSONArray().put(tool))
            .put("tool_choice", "auto")
        return JSONObject().put("type", "session.update").put("session", session)
    }

    fun parse(text: String): ProtocolEvent = runCatching {
        val root = JSONObject(text)
        when (root.optString("type")) {
            "session.updated" -> ProtocolEvent.Ready
            "response.audio.delta", "response.output_audio.delta" -> ProtocolEvent.Audio(
                ProtocolJson.decodeAudio(root.getString("delta")),
                24_000,
            )
            "response.audio_transcript.delta", "response.output_audio_transcript.delta" ->
                ProtocolEvent.Transcript(root.optString("delta"))
            "conversation.item.input_audio_transcription.completed" ->
                ProtocolEvent.UserTranscript(root.optString("transcript"))
            "response.output_item.done" -> {
                val item = root.optJSONObject("item") ?: return ProtocolEvent.Ignored
                if (
                    item.optString("type") == "function_call" &&
                    item.optString("name") == "navigate_to"
                ) {
                    ProtocolEvent.DestinationSearchRequested(
                        ProtocolJson.destinationSearch(JSONObject(item.optString("arguments", "{}"))),
                    )
                } else ProtocolEvent.Ignored
            }
            "response.done" -> ProtocolEvent.Complete
            "error" -> ProtocolEvent.Failure(
                ProviderFailure(
                    ProviderFailureKind.REJECTED_REQUEST,
                    ProtocolJson.providerDiagnostic(root),
                ),
            )
            else -> ProtocolEvent.Ignored
        }
    }.getOrElse { ProtocolEvent.Failure(ProviderFailure.INVALID_RESPONSE) }
}

internal fun defaultRealtimeHttpClient(): OkHttpClient = RealtimeHttpClient.instance

private object RealtimeHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
