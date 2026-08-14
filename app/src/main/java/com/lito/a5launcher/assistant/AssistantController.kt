package com.lito.a5launcher.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lito.a5launcher.R
import com.lito.a5launcher.location.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call

data class AssistantUiState(
    val status: AssistantState = AssistantState.Disabled,
    val response: ConversationResult? = null,
    val audioLevel: Float = 0f,
    val action: AssistantAction? = null,
    val heardText: String? = null,
)

interface AssistantCredentialTester {
    fun testConnection(
        kind: AssistantCredentialKind,
        apiKey: String,
        onResult: (AssistantCredentialTestResult) -> Unit,
    )

    fun cancelConnectionTest()
}

data class AssistantCredentialTestResult(val successful: Boolean, val message: String)

internal class AssistantController(
    context: Context,
    private val scope: CoroutineScope,
    private val onNavigate: (NavigationRequest) -> Boolean,
    private val locationRepository: LocationRepository,
    openAiProvider: RealtimeVoiceProvider? = null,
    geminiProvider: RealtimeVoiceProvider? = null,
) : AssistantCredentialTester {
    private val appContext = context.applicationContext
    val settings = AssistantSettings(appContext)
    private val audio = AssistantAudio(appContext)
    private val errorLogger = AssistantErrorLogger(appContext)
    private val destinationResolver by lazy { GooglePlacesDestinationResolver() }
    private val openAiProvider by lazy { openAiProvider ?: OpenAiRealtimeProvider() }
    private val geminiProvider by lazy { geminiProvider ?: GeminiLiveProvider() }
    private val connectionTester by lazy { ProviderConnectionTester() }
    private val _uiState = MutableStateFlow(AssistantUiState(initialState()))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()
    private val history = ArrayDeque<ConversationTurn>(MAX_HISTORY_TURNS)
    private val outputAudio = PcmAccumulator()
    private val outputTranscript = StringBuilder()
    private val inputTranscript = StringBuilder()
    private var session: RealtimeVoiceProvider.Session? = null
    private var timeoutJob: Job? = null
    private var errorDismissJob: Job? = null
    private var actionLaunchJob: Job? = null
    private var destinationCall: Call? = null
    private var connectionTestCancel: (() -> Unit)? = null
    private var connectionTestId = 0L
    private var actionHandled = false
    private var activeProvider: RealtimeVoiceProvider? = null
    private var observedProvider = settings.provider
    @Volatile private var activeTurnId = 0L

    fun refreshSettings() {
        val configuredProvider = settings.provider
        if (configuredProvider != observedProvider) {
            cancelActiveTurn(clearResponse = true)
            history.clear()
            observedProvider = configuredProvider
        }
        if (session == null) _uiState.value = AssistantUiState(initialState())
    }

    fun startTurn() {
        val provider = settings.provider
        if (provider == AssistantProvider.DISABLED) {
            _uiState.value = AssistantUiState(AssistantState.Disabled)
            return
        }
        val key = settings.apiKey(provider)
        if (key.isNullOrBlank()) {
            _uiState.value = AssistantUiState(AssistantState.Error(text(R.string.assistant_error_api_key)))
            return
        }
        if (!networkAvailable()) {
            _uiState.value = AssistantUiState(AssistantState.Offline(text(R.string.assistant_error_offline)))
            return
        }
        cancelActiveTurn(clearResponse = false)
        val turnId = ++activeTurnId
        outputAudio.reset()
        outputTranscript.clear()
        inputTranscript.clear()
        actionHandled = false
        // Connecting is intentionally silent: "Listening" is only shown once
        // the provider is ready and microphone capture has actually started.
        _uiState.value = AssistantUiState(AssistantState.Ready)
        val request = AssistantSessionRequest(
            localeTag = Locale.getDefault().toLanguageTag(),
            history = history.toList(),
        )
        activeProvider = provider(provider)
        session = activeProvider?.connect(key, request, listenerFor(turnId, provider))
        timeoutJob = scope.launch {
            delay(TURN_TIMEOUT_MS)
            recordFailure(
                provider,
                AssistantFailurePhase.TURN,
                ProviderFailure(
                    ProviderFailureKind.CONNECTION,
                    ProviderDiagnostic(code = "TIMEOUT", message = "No response after ${TURN_TIMEOUT_MS / 1000}s"),
                ),
            )
            fail(text(R.string.assistant_error_timeout), turnId)
        }
    }

    fun repeatResponse() {
        _uiState.value.response?.let {
            _uiState.value = _uiState.value.copy(status = AssistantState.Speaking)
            audio.play(it.audioPcm16, it.sampleRate)
        }
    }

    override fun testConnection(
        kind: AssistantCredentialKind,
        apiKey: String,
        onResult: (AssistantCredentialTestResult) -> Unit,
    ) {
        cancelConnectionTest()
        if (apiKey.isBlank()) {
            onResult(AssistantCredentialTestResult(false, text(R.string.assistant_error_select_provider)))
            return
        }
        if (!networkAvailable()) {
            onResult(AssistantCredentialTestResult(false, text(R.string.assistant_error_offline)))
            return
        }
        if (kind == AssistantCredentialKind.PLACES) {
            testPlacesConnection(apiKey, onResult)
            return
        }
        val provider = when (kind) {
            AssistantCredentialKind.OPENAI -> AssistantProvider.OPENAI
            AssistantCredentialKind.GEMINI -> AssistantProvider.GEMINI
            AssistantCredentialKind.PLACES -> error("Places handled separately")
        }
        val testId = ++connectionTestId
        val completed = AtomicBoolean(false)
        var connectionTest: ProviderConnectionTest? = null
        val testTimeout = scope.launch {
            delay(CONNECTION_TEST_TIMEOUT_MS)
            if (testId != connectionTestId) return@launch
            if (!completed.compareAndSet(false, true)) return@launch
            connectionTest?.cancel()
            connectionTestCancel = null
            recordFailure(
                provider,
                AssistantFailurePhase.CONNECTION_TEST,
                ProviderFailure(
                    ProviderFailureKind.CONNECTION,
                    ProviderDiagnostic(
                        code = "TIMEOUT",
                        message = "No response after ${CONNECTION_TEST_TIMEOUT_MS / 1000}s",
                    ),
                ),
            ) { onResult(AssistantCredentialTestResult(false, text(R.string.assistant_error_connection_test))) }
        }
        connectionTestCancel = {
            completed.set(true)
            testTimeout.cancel()
            connectionTest?.cancel()
        }
        connectionTest = connectionTester.test(provider, apiKey.trim()) { failure ->
            if (testId != connectionTestId || !completed.compareAndSet(false, true)) return@test
            testTimeout.cancel()
            connectionTestCancel = null
            if (failure == null) {
                scope.launchMain {
                    onResult(AssistantCredentialTestResult(true, text(R.string.assistant_connection_ok)))
                }
            } else {
                val result = failure.diagnostic?.displayText()?.takeIf(String::isNotBlank)
                    ?: failureText(failure)
                recordFailure(provider, AssistantFailurePhase.CONNECTION_TEST, failure) {
                    onResult(AssistantCredentialTestResult(false, result))
                }
            }
        }
    }

    private fun testPlacesConnection(
        apiKey: String,
        onResult: (AssistantCredentialTestResult) -> Unit,
    ) {
        val testId = ++connectionTestId
        val completed = AtomicBoolean(false)
        var call: Call? = null
        val timeout = scope.launch {
            delay(CONNECTION_TEST_TIMEOUT_MS)
            if (testId != connectionTestId) return@launch
            if (!completed.compareAndSet(false, true)) return@launch
            call?.cancel()
            connectionTestCancel = null
            val failure = ProviderFailure(
                ProviderFailureKind.CONNECTION,
                ProviderDiagnostic(
                    code = "TIMEOUT",
                    message = "No response after ${CONNECTION_TEST_TIMEOUT_MS / 1000}s",
                ),
            )
            recordFailure(
                AssistantFailureSource.GOOGLE_PLACES,
                AssistantFailurePhase.CONNECTION_TEST,
                failure,
            ) { onResult(AssistantCredentialTestResult(false, failure.diagnostic!!.displayText())) }
        }
        connectionTestCancel = {
            completed.set(true)
            timeout.cancel()
            call?.cancel()
        }
        call = destinationResolver.resolve(
            apiKey = apiKey.trim(),
            search = DestinationSearch("Ordes", DestinationSearchMode.RELEVANCE),
            location = null,
            localeTag = Locale.getDefault().toLanguageTag(),
        ) { result ->
            if (testId != connectionTestId || !completed.compareAndSet(false, true)) return@resolve
            timeout.cancel()
            connectionTestCancel = null
            when (result) {
                is DestinationResolution.Failure -> {
                    val failure = ProviderFailure(ProviderFailureKind.REJECTED_REQUEST, result.diagnostic)
                    recordFailure(
                        AssistantFailureSource.GOOGLE_PLACES,
                        AssistantFailurePhase.CONNECTION_TEST,
                        failure,
                    ) { onResult(AssistantCredentialTestResult(false, result.diagnostic.displayText())) }
                }
                DestinationResolution.NotFound, is DestinationResolution.Found ->
                    scope.launchMain {
                        onResult(AssistantCredentialTestResult(true, text(R.string.assistant_connection_ok)))
                    }
            }
        }
    }

    override fun cancelConnectionTest() {
        connectionTestId++
        connectionTestCancel?.invoke()
        connectionTestCancel = null
    }

    fun closeConversation() {
        cancelActiveTurn(clearResponse = true)
        history.clear()
        _uiState.value = AssistantUiState(initialState())
    }

    fun errorLogStats(): AssistantErrorLogStats = errorLogger.stats()

    fun errorLogExportName(): String = errorLogger.suggestedExportName()

    fun exportErrorLogs(destination: android.net.Uri, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val exported = errorLogger.export(destination)
            scope.launchMain { onResult(exported) }
        }
    }

    fun clearErrorLogs(onResult: (Int) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val deleted = errorLogger.clear()
            scope.launchMain { onResult(deleted) }
        }
    }

    fun cancelActiveTurn(clearResponse: Boolean = false) {
        activeTurnId++
        timeoutJob?.cancel()
        errorDismissJob?.cancel()
        actionLaunchJob?.cancel()
        timeoutJob = null
        errorDismissJob = null
        actionLaunchJob = null
        destinationCall?.cancel()
        destinationCall = null
        audio.stopRecording()
        audio.stopPlayback()
        session?.cancel()
        session = null
        activeProvider = null
        if (clearResponse) _uiState.value = _uiState.value.copy(response = null)
    }

    fun release() {
        cancelConnectionTest()
        cancelActiveTurn(clearResponse = true)
        history.clear()
        audio.release()
    }

    private fun listenerFor(turnId: Long, provider: AssistantProvider) = object : RealtimeVoiceProvider.Listener {
        override fun onReady() {
            scope.launchMain {
            if (!isActive(turnId)) return@launchMain
            _uiState.value = _uiState.value.copy(status = AssistantState.Listening)
            audio.recordClosedTurn(
                sampleRate = activeProvider?.inputSampleRate ?: 16_000,
                onChunk = { chunk -> if (isActive(turnId)) session?.sendAudio(chunk) },
                onLevel = { amplitude ->
                    scope.launchMain {
                        if (!isActive(turnId) || _uiState.value.status != AssistantState.Listening) {
                            return@launchMain
                        }
                        val target = (amplitude / AUDIO_LEVEL_REFERENCE).coerceIn(0f, 1f)
                        val previous = _uiState.value.audioLevel
                        _uiState.value = _uiState.value.copy(
                            audioLevel = previous + (target - previous) * AUDIO_LEVEL_SMOOTHING,
                        )
                    }
                },
                onFinished = { result ->
                    scope.launchMain {
                        if (!isActive(turnId) || session == null) return@launchMain
                        when (result) {
                            SpeechCaptureResult.SPEECH -> {
                                _uiState.value = _uiState.value.copy(status = AssistantState.Processing)
                                session?.finishAudio()
                            }
                            SpeechCaptureResult.NO_SPEECH -> finishSilentTurn()
                            SpeechCaptureResult.CANCELLED -> Unit
                        }
                    }
                },
                onFailure = { fail(it, turnId) },
            )
            }
        }

        override fun onAudio(pcm16: ByteArray, sampleRate: Int) {
            if (isActive(turnId)) outputAudio.append(pcm16)
        }

        override fun onUserTranscript(text: String) {
            scope.launchMain {
                if (!isActive(turnId)) return@launchMain
                inputTranscript.append(text)
                _uiState.value = _uiState.value.copy(
                    heardText = inputTranscript.toString().trim().takeIf(String::isNotBlank),
                )
            }
        }

        override fun onTranscriptDelta(text: String) {
            if (isActive(turnId)) outputTranscript.append(text)
        }

        override fun onDestinationSearch(search: DestinationSearch) {
            scope.launchMain {
                if (!isActive(turnId) || actionHandled) return@launchMain
                if (search.query.isBlank()) {
                    finishConversation(text(R.string.assistant_destination_empty))
                    return@launchMain
                }
                val route = destinationSearchRoute(search, currentKnownLocation())
                when (route) {
                    is DestinationSearchRoute.TextNavigation -> {
                        openDestinationSearch(route.query)
                        return@launchMain
                    }
                    DestinationSearchRoute.LocationUnavailable -> {
                        finishConversation(text(R.string.assistant_destination_location_unavailable))
                        return@launchMain
                    }
                    is DestinationSearchRoute.NearbySearch -> Unit
                }
                val placesKey = settings.placesApiKey()
                if (placesKey.isNullOrBlank()) {
                    finishConversation(text(R.string.assistant_error_places_key))
                    return@launchMain
                }
                _uiState.value = _uiState.value.copy(
                    status = AssistantState.Processing,
                    action = AssistantAction.Searching(search.query),
                )
                destinationCall?.cancel()
                destinationCall = destinationResolver.resolve(
                    apiKey = placesKey,
                    search = route.search,
                    location = route.location,
                    localeTag = Locale.getDefault().toLanguageTag(),
                ) { result ->
                    scope.launchMain {
                        if (!isActive(turnId) || actionHandled) return@launchMain
                        destinationCall = null
                        when (result) {
                            is DestinationResolution.Found -> openResolvedDestination(result.destination)
                            DestinationResolution.NotFound -> finishConversation(
                                text(R.string.assistant_destination_not_found),
                            )
                            is DestinationResolution.Failure -> {
                                recordFailure(
                                    AssistantFailureSource.GOOGLE_PLACES,
                                    AssistantFailurePhase.DESTINATION_SEARCH,
                                    ProviderFailure(
                                        ProviderFailureKind.REJECTED_REQUEST,
                                        result.diagnostic,
                                    ),
                                )
                                finishConversation(text(R.string.assistant_destination_search_failed))
                            }
                        }
                    }
                }
            }
        }

        override fun onTurnComplete() {
            scope.launchMain {
                if (isActive(turnId) && !actionHandled) finishConversation(outputTranscript.toString().trim())
            }
        }

        override fun onFailure(failure: ProviderFailure) {
            if (!isActive(turnId)) return
            recordFailure(provider, AssistantFailurePhase.TURN, failure)
            fail(failureText(failure), turnId)
        }
    }

    private fun openResolvedDestination(destination: NavigationDestination) {
        executeNavigation(
            NavigationRequest.Coordinates(destination),
            destination.name,
        )
    }

    private fun openDestinationSearch(query: String) {
        executeNavigation(NavigationRequest.SearchText(query), query)
    }

    private fun executeNavigation(request: NavigationRequest, label: String) {
        actionHandled = true
        finishJobs()
        audio.stopRecording()
        session?.cancel()
        session = null
        activeProvider = null
        _uiState.value = AssistantUiState(
            status = AssistantState.Processing,
            action = AssistantAction.Navigating(label),
            heardText = inputTranscript.toString().trim().takeIf(String::isNotBlank),
        )
        actionLaunchJob?.cancel()
        actionLaunchJob = scope.launch {
            delay(ACTION_CONFIRMATION_MS)
            actionLaunchJob = null
            val opened = onNavigate(request)
            activeTurnId++
            _uiState.value = AssistantUiState(
                if (opened) initialState()
                else AssistantState.Error(text(R.string.assistant_error_no_navigation)),
            )
        }
    }

    private fun finishConversation(transcript: String) {
        finishJobs()
        session = null
        activeProvider = null
        val spoken = transcript.ifBlank { text(R.string.assistant_response_finished) }
        val response = ConversationResult(spoken, outputAudio.bytes())
        val heardText = inputTranscript.toString().trim().takeIf(String::isNotBlank)
        heardText?.let {
            appendHistory(ConversationTurn(ConversationRole.USER, it))
        }
        appendHistory(ConversationTurn(ConversationRole.ASSISTANT, spoken))
        _uiState.value = AssistantUiState(
            status = AssistantState.Speaking,
            response = response,
            action = AssistantAction.AnswerReady,
            heardText = heardText,
        )
        audio.play(response.audioPcm16, response.sampleRate)
    }

    private fun fail(message: String, turnId: Long? = null) {
        scope.launchMain {
            if (turnId != null && !isActive(turnId)) return@launchMain
            finishJobs()
            audio.stopRecording()
            session?.cancel()
            session = null
            activeProvider = null
            _uiState.value = AssistantUiState(AssistantState.Error(message), _uiState.value.response)
            errorDismissJob?.cancel()
            errorDismissJob = scope.launch {
                delay(ASSISTANT_MESSAGE_VISIBLE_MS)
                val error = _uiState.value.status as? AssistantState.Error
                if (session == null && error?.message == message) {
                    _uiState.value = _uiState.value.copy(status = initialState())
                }
            }
        }
    }

    private fun finishSilentTurn() {
        finishJobs()
        session?.cancel()
        session = null
        activeProvider = null
        _uiState.value = AssistantUiState(initialState())
    }

    private fun finishJobs() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun appendHistory(turn: ConversationTurn) {
        if (history.size == MAX_HISTORY_TURNS) history.removeFirst()
        history.addLast(turn)
    }

    private fun isActive(turnId: Long): Boolean = activeTurnId == turnId && session != null

    private fun provider(provider: AssistantProvider): RealtimeVoiceProvider = when (provider) {
        AssistantProvider.OPENAI -> openAiProvider
        AssistantProvider.GEMINI -> geminiProvider
        AssistantProvider.DISABLED -> error("Disabled provider cannot connect")
    }

    private fun initialState(): AssistantState = if (
        settings.provider == AssistantProvider.DISABLED
    ) AssistantState.Disabled else AssistantState.Ready

    private fun text(resource: Int): String = appContext.getString(resource)

    private fun issueText(issue: DestinationIssue): String = text(when (issue) {
        DestinationIssue.AMBIGUOUS -> R.string.assistant_destination_ambiguous
        DestinationIssue.EMPTY_NAME -> R.string.assistant_destination_empty
        DestinationIssue.INVALID_LATITUDE -> R.string.assistant_destination_latitude
        DestinationIssue.INVALID_LONGITUDE -> R.string.assistant_destination_longitude
    })

    private fun failureText(failure: ProviderFailure): String = text(when (failure.kind) {
        ProviderFailureKind.CONNECTION -> R.string.assistant_error_provider_connection
        ProviderFailureKind.REJECTED_REQUEST -> R.string.assistant_error_provider_rejected
        ProviderFailureKind.INVALID_RESPONSE -> R.string.assistant_error_provider_response
    })

    private fun recordFailure(
        provider: AssistantProvider,
        phase: AssistantFailurePhase,
        failure: ProviderFailure,
        onRecorded: (() -> Unit)? = null,
    ) = recordFailure(AssistantFailureSource.from(provider), phase, failure, onRecorded)

    private fun recordFailure(
        source: AssistantFailureSource,
        phase: AssistantFailurePhase,
        failure: ProviderFailure,
        onRecorded: (() -> Unit)? = null,
    ) {
        if (!settings.errorLoggingEnabled) {
            onRecorded?.let { scope.launchMain(it) }
            return
        }
        scope.launch(Dispatchers.IO) {
            errorLogger.record(source, phase, failure)
            onRecorded?.let { scope.launchMain(it) }
        }
    }

    private fun networkAvailable(): Boolean {
        val manager = ContextCompat.getSystemService(appContext, ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun currentKnownLocation(): KnownLocation? {
        val position = locationRepository.state.value.activePosition(
            SystemClock.elapsedRealtime(),
        ) ?: return null
        return KnownLocation(
            latitude = position.latitude,
            longitude = position.longitude,
        )
    }

    private fun CoroutineScope.launchMain(block: () -> Unit) = launch(kotlinx.coroutines.Dispatchers.Main) { block() }

    private companion object {
        const val TURN_TIMEOUT_MS = 30_000L
        const val ACTION_CONFIRMATION_MS = 450L
        const val MAX_HISTORY_TURNS = 8
        const val CONNECTION_TEST_TIMEOUT_MS = 10_000L
        const val AUDIO_LEVEL_REFERENCE = 3_000f
        const val AUDIO_LEVEL_SMOOTHING = .42f
    }
}
