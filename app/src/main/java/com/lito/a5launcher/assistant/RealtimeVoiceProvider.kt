package com.lito.a5launcher.assistant

interface RealtimeVoiceProvider {
    val inputSampleRate: Int

    fun connect(
        apiKey: String,
        request: AssistantSessionRequest,
        listener: Listener,
    ): Session

    interface Session {
        fun sendAudio(pcm16: ByteArray)
        fun finishAudio()
        fun cancel()
    }

    interface Listener {
        fun onReady()
        fun onAudio(pcm16: ByteArray, sampleRate: Int)
        fun onUserTranscript(text: String)
        fun onTranscriptDelta(text: String)
        fun onDestinationSearch(search: DestinationSearch)
        fun onTurnComplete()
        fun onFailure(failure: ProviderFailure)
    }
}

enum class ProviderFailureKind { CONNECTION, REJECTED_REQUEST, INVALID_RESPONSE }

data class ProviderDiagnostic(
    val httpStatus: Int? = null,
    val code: String? = null,
    val message: String? = null,
) {
    fun displayText(): String = listOfNotNull(
        httpStatus?.let { "HTTP $it" },
        code?.takeIf(String::isNotBlank),
        message?.takeIf(String::isNotBlank),
    ).distinct().joinToString(" · ")
}

data class ProviderFailure(
    val kind: ProviderFailureKind,
    val diagnostic: ProviderDiagnostic? = null,
) {
    companion object {
        val CONNECTION = ProviderFailure(ProviderFailureKind.CONNECTION)
        val REJECTED_REQUEST = ProviderFailure(ProviderFailureKind.REJECTED_REQUEST)
        val INVALID_RESPONSE = ProviderFailure(ProviderFailureKind.INVALID_RESPONSE)
    }
}

internal object AssistantPrompt {
    fun build(request: AssistantSessionRequest): String = buildString {
        append(
            "You are the concise voice assistant of an in-car launcher. " +
                "Always answer in the language represented by locale ${request.localeTag}. " +
                "Never claim an action happened unless a tool is called. " +
                "For any request to travel, navigate, find a nearby place or open a route, " +
                "call navigate_to with the user's destination wording. " +
                "Set relative_to_current_location=true only for nearby or closest-place requests. " +
                "Never invent coordinates or claim navigation started before the tool is called. " +
                "Keep spoken answers short and driving-safe."
        )
    }

    val destinationSearchParametersJson = """
        {
          "type":"object",
          "properties":{
            "destination":{"type":"string","description":"Destination exactly as requested by the user"},
            "relative_to_current_location":{"type":"boolean"}
          },
          "required":["destination","relative_to_current_location"]
        }
    """.trimIndent()
}
