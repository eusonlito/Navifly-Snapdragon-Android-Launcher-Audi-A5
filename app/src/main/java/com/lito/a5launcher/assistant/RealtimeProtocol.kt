package com.lito.a5launcher.assistant

import org.json.JSONObject
import java.util.Base64

internal sealed interface ProtocolEvent {
    data object Ready : ProtocolEvent
    data class Audio(val bytes: ByteArray, val sampleRate: Int) : ProtocolEvent
    data class UserTranscript(val text: String) : ProtocolEvent
    data class Transcript(val text: String) : ProtocolEvent
    data class DestinationSearchRequested(val search: DestinationSearch) : ProtocolEvent
    data object Complete : ProtocolEvent
    data class Failure(val failure: ProviderFailure) : ProtocolEvent
    data object Ignored : ProtocolEvent
}

internal object ProtocolJson {
    fun destinationSearch(arguments: JSONObject): DestinationSearch = DestinationSearch(
        query = arguments.optString("destination").trim(),
        mode = if (arguments.optBoolean("relative_to_current_location", false)) {
            DestinationSearchMode.NEAREST
        } else {
            DestinationSearchMode.RELEVANCE
        },
    )

    fun decodeAudio(value: String): ByteArray = Base64.getDecoder().decode(value)

    fun providerDiagnostic(
        payload: JSONObject?,
        httpStatus: Int? = null,
        fallbackMessage: String? = null,
    ): ProviderDiagnostic? {
        val error = payload?.optJSONObject("error") ?: payload
        val codes = listOfNotNull(
            error?.optString("status")?.takeIf(String::isNotBlank),
            error?.optString("code")?.takeIf(String::isNotBlank),
            error?.optString("type")?.takeIf(String::isNotBlank),
        ).distinct().joinToString(" / ").takeIf(String::isNotBlank)
        val message = error?.optString("message")?.takeIf(String::isNotBlank)
            ?: fallbackMessage?.takeIf(String::isNotBlank)
        if (httpStatus == null && codes == null && message == null) return null
        return ProviderDiagnostic(
            httpStatus = httpStatus,
            code = codes?.let(::sanitizeDiagnostic),
            message = message?.let(::sanitizeDiagnostic),
        )
    }

    fun providerDiagnostic(
        body: String?,
        httpStatus: Int,
        fallbackMessage: String?,
    ): ProviderDiagnostic = providerDiagnostic(
        payload = body?.let { runCatching { JSONObject(it) }.getOrNull() },
        httpStatus = httpStatus,
        fallbackMessage = fallbackMessage,
    ) ?: ProviderDiagnostic(httpStatus = httpStatus)

    private fun sanitizeDiagnostic(value: String): String = value
        .replace(
            Regex("(?i)(api[_ -]?key(?:\\s+provided)?\\s*[=:]\\s*)[^\\s,;]+"),
            "$1••••",
        )
        .replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "Bearer ••••")
        .replace(Regex("AIza[A-Za-z0-9_-]{12,}"), "AIza••••")
        .replace(Regex("sk-[A-Za-z0-9_*•.-]{4,}"), "sk-••••")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(500)
}

internal fun ProtocolEvent.dispatchTo(
    listener: RealtimeVoiceProvider.Listener,
    onComplete: () -> Unit,
) {
    when (this) {
        ProtocolEvent.Ready -> listener.onReady()
        is ProtocolEvent.Audio -> listener.onAudio(bytes, sampleRate)
        is ProtocolEvent.UserTranscript -> listener.onUserTranscript(text)
        is ProtocolEvent.Transcript -> listener.onTranscriptDelta(text)
        is ProtocolEvent.DestinationSearchRequested -> listener.onDestinationSearch(search)
        ProtocolEvent.Complete -> onComplete()
        is ProtocolEvent.Failure -> listener.onFailure(failure)
        ProtocolEvent.Ignored -> Unit
    }
}
