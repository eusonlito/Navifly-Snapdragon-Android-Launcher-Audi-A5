package com.lito.a5launcher.assistant

internal const val ASSISTANT_MESSAGE_VISIBLE_MS = 3_000L

enum class AssistantProvider(
    val model: String,
) {
    DISABLED(""),
    OPENAI("gpt-realtime-2.1-mini"),
    GEMINI("gemini-3.1-flash-live-preview"),
}

enum class AssistantCredentialKind(val model: String) {
    OPENAI(AssistantProvider.OPENAI.model),
    GEMINI(AssistantProvider.GEMINI.model),
    PLACES(""),
}

sealed interface AssistantState {
    data object Disabled : AssistantState
    data object Ready : AssistantState
    data object Listening : AssistantState
    data object Processing : AssistantState
    data object Speaking : AssistantState
    data class Offline(val message: String) : AssistantState
    data class Error(val message: String) : AssistantState
}

sealed interface AssistantAction {
    data class Searching(val query: String) : AssistantAction
    data class Navigating(val destination: String) : AssistantAction
    data object AnswerReady : AssistantAction
}

data class KnownLocation(
    val latitude: Double,
    val longitude: Double,
    val ageMillis: Long,
)

data class NavigationDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val ambiguous: Boolean = false,
)

enum class DestinationSearchMode { RELEVANCE, NEAREST }

data class DestinationSearch(
    val query: String,
    val mode: DestinationSearchMode = DestinationSearchMode.RELEVANCE,
)

sealed interface DestinationResolution {
    data class Found(val destination: NavigationDestination) : DestinationResolution
    data object NotFound : DestinationResolution
    data class Failure(val diagnostic: ProviderDiagnostic) : DestinationResolution
}

sealed interface NavigationRequest {
    data class Coordinates(val destination: NavigationDestination) : NavigationRequest
    data class SearchText(val query: String) : NavigationRequest
}

data class ConversationResult(
    val transcript: String,
    val audioPcm16: ByteArray,
    val sampleRate: Int = 24_000,
)

enum class ConversationRole(val promptValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

data class ConversationTurn(val role: ConversationRole, val text: String)

data class AssistantSessionRequest(
    val localeTag: String,
    val history: List<ConversationTurn>,
)

internal sealed interface DestinationSearchRoute {
    data class TextNavigation(val query: String) : DestinationSearchRoute
    data class NearbySearch(
        val search: DestinationSearch,
        val location: KnownLocation,
    ) : DestinationSearchRoute
    data object LocationUnavailable : DestinationSearchRoute
}

internal fun destinationSearchRoute(
    search: DestinationSearch,
    location: KnownLocation?,
): DestinationSearchRoute = when (search.mode) {
    DestinationSearchMode.RELEVANCE -> DestinationSearchRoute.TextNavigation(search.query)
    DestinationSearchMode.NEAREST -> location?.let {
        DestinationSearchRoute.NearbySearch(search, it)
    } ?: DestinationSearchRoute.LocationUnavailable
}

sealed interface DestinationValidation {
    data class Valid(val destination: NavigationDestination) : DestinationValidation
    data class Invalid(val issue: DestinationIssue) : DestinationValidation
    data class NeedsClarification(val issue: DestinationIssue) : DestinationValidation
}

enum class DestinationIssue { AMBIGUOUS, EMPTY_NAME, INVALID_LATITUDE, INVALID_LONGITUDE }

object DestinationValidator {
    fun validate(destination: NavigationDestination): DestinationValidation {
        if (destination.ambiguous) {
            return DestinationValidation.NeedsClarification(DestinationIssue.AMBIGUOUS)
        }
        if (destination.name.isBlank()) {
            return DestinationValidation.Invalid(DestinationIssue.EMPTY_NAME)
        }
        if (!destination.latitude.isFinite() || destination.latitude !in -90.0..90.0) {
            return DestinationValidation.Invalid(DestinationIssue.INVALID_LATITUDE)
        }
        if (!destination.longitude.isFinite() || destination.longitude !in -180.0..180.0) {
            return DestinationValidation.Invalid(DestinationIssue.INVALID_LONGITUDE)
        }
        return DestinationValidation.Valid(destination)
    }
}
