package com.lito.a5launcher.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lito.a5launcher.R
import com.lito.a5launcher.ui.components.SettingsSegmentedSelector
import com.lito.a5launcher.ui.components.SettingsActionButton
import com.lito.a5launcher.ui.components.CommandSurface
import com.lito.a5launcher.ui.components.SettingsDimensions
import com.lito.a5launcher.ui.components.SettingsPalette
import com.lito.a5launcher.ui.components.FLOATING_NOTIFICATION_VISIBLE_MS
import com.lito.a5launcher.ui.components.FloatingNotification
import com.lito.a5launcher.ui.components.FloatingNotificationBanner
import com.lito.a5launcher.ui.components.FloatingNotificationHost
import com.lito.a5launcher.ui.components.FloatingNotificationTone
import com.lito.a5launcher.ui.components.formatStorageSize
import kotlinx.coroutines.delay

@Composable
fun AssistantRobotButton(
    buttonSize: Dp,
    iconSize: Dp,
    active: Boolean,
    onClick: () -> Unit,
) {
    val activeScale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = tween(160),
        label = "assistantCommandState",
    )
    CommandSurface(buttonSize, onClick) {
        Image(
            painter = painterResource(R.drawable.assistant_robot),
            contentDescription = stringResource(R.string.assistant_voice),
            modifier = Modifier
                .size(width = iconSize * 1.12f, height = iconSize * .82f)
                .graphicsLayer {
                    scaleX = activeScale
                    scaleY = activeScale
                    alpha = if (active) 1f else .92f
                },
        )
    }
}

@Composable
fun AssistantStatusPanel(
    state: AssistantState,
    audioLevel: Float,
    action: AssistantAction?,
    heardText: String?,
    modifier: Modifier = Modifier,
) {
    val text = when (state) {
        AssistantState.Disabled, AssistantState.Ready -> null
        AssistantState.Listening -> stringResource(R.string.assistant_listening)
        AssistantState.Processing -> stringResource(R.string.assistant_processing)
        AssistantState.Speaking -> stringResource(R.string.assistant_responding)
        is AssistantState.Offline -> state.message
        is AssistantState.Error -> state.message
    }
    val detail = when (action) {
        is AssistantAction.Searching -> stringResource(
            R.string.assistant_action_searching,
            action.query,
        )
        is AssistantAction.Navigating -> stringResource(
            R.string.assistant_action_navigating,
            action.destination,
        )
        AssistantAction.AnswerReady -> stringResource(R.string.assistant_action_answer_ready)
        null -> heardText?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.assistant_action_understood, it)
        }
    }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        FloatingNotificationBanner(
            text = text.orEmpty(),
            detail = detail,
            color = when (state) {
                is AssistantState.Error -> Color(0xFFFF7777)
                is AssistantState.Offline -> Color(0xFFFFC857)
                else -> Color.White
            },
        ) {
            audioLevel.takeIf { state == AssistantState.Listening }?.let { AssistantAudioMeter(it) }
        }
    }
}

@Composable
private fun AssistantAudioMeter(level: Float) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "assistantAudioLevel",
    )
    val multipliers = listOf(.65f, .9f, 1.15f, .9f, .65f)
    Row(
        modifier = Modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        multipliers.forEach { multiplier ->
            val height = (5f + 17f * animatedLevel * multiplier).coerceAtMost(22f)
            Box(
                Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF63E6F5)),
            )
        }
    }
}

@Composable
fun AssistantConversationDialog(
    response: ConversationResult,
    onRespond: () -> Unit,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .58f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(.58f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0D181D))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(18.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                response.transcript,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AssistantResponseAction(stringResource(R.string.assistant_respond), onRespond)
                AssistantResponseAction(stringResource(R.string.assistant_repeat), onRepeat)
                AssistantResponseAction(stringResource(R.string.assistant_close), onClose)
            }
        }
    }
}

@Composable
private fun AssistantResponseAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF213239))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AssistantSettingsPanel(
    settings: AssistantSettings,
    credentialTester: AssistantCredentialTester,
    readErrorLogStats: () -> AssistantErrorLogStats,
    onExportErrorLogs: ((Boolean?) -> Unit) -> Unit,
    onClearErrorLogs: ((Int) -> Unit) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var provider by remember { mutableStateOf(settings.provider) }
    var credentialTab by remember {
        mutableStateOf(
            if (provider == AssistantProvider.GEMINI) AssistantCredentialKind.GEMINI
            else AssistantCredentialKind.OPENAI,
        )
    }
    val apiKeys = remember {
        mutableStateMapOf<AssistantCredentialKind, String>().apply {
            AssistantCredentialKind.entries.forEach { put(it, settings.apiKey(it).orEmpty()) }
        }
    }
    var message by remember { mutableStateOf("") }
    var errorLogMessage by remember { mutableStateOf("") }
    var testRequestId by remember { mutableIntStateOf(0) }
    var errorLoggingEnabled by remember { mutableStateOf(settings.errorLoggingEnabled) }
    var errorLogStats by remember { mutableStateOf(readErrorLogStats()) }
    var saveNotice by remember { mutableStateOf<FloatingNotification?>(null) }
    val disabledLabel = stringResource(R.string.assistant_disabled).uppercase()
    val noErrorsMessage = stringResource(R.string.assistant_no_error_logs)
    val exportFailedMessage = stringResource(R.string.assistant_error_export_failed)
    val exportedTemplate = stringResource(R.string.assistant_errors_exported)
    val clearedTemplate = stringResource(R.string.assistant_errors_cleared)
    val openAiCredentialLabel = stringResource(R.string.assistant_credential_openai)
    val geminiCredentialLabel = stringResource(R.string.assistant_credential_gemini)
    val placesCredentialLabel = stringResource(R.string.assistant_credential_places)
    val placesService = stringResource(R.string.assistant_places_service)
    val keySavedMessage = stringResource(R.string.assistant_key_saved)
    val validatingMessage = stringResource(R.string.assistant_validating)
    val isSaving = saveNotice?.tone == FloatingNotificationTone.PROGRESS

    LaunchedEffect(credentialTab) {
        credentialTester.cancelConnectionTest()
    }

    LaunchedEffect(saveNotice) {
        if (saveNotice != null && saveNotice?.tone != FloatingNotificationTone.PROGRESS) {
            delay(FLOATING_NOTIFICATION_VISIBLE_MS)
            saveNotice = null
        }
    }

    Box(modifier) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
            AssistantSettingsLabel(stringResource(R.string.assistant_provider))
            Spacer(Modifier.height(5.dp))
            SettingsSegmentedSelector(
                options = AssistantProvider.entries,
                selected = provider,
                label = {
                    when (it) {
                        AssistantProvider.DISABLED -> disabledLabel
                        AssistantProvider.OPENAI -> "OPENAI"
                        AssistantProvider.GEMINI -> "GOOGLE GEMINI"
                    }
                },
                controlHeight = SettingsDimensions.SelectorHeight,
                onSelected = { option ->
                    provider = option
                    settings.provider = option
                    testRequestId++
                    message = ""
                    onSaved()
                },
            )
            Spacer(Modifier.height(8.dp))
            AssistantSettingsLabel(stringResource(R.string.assistant_error_logging))
            Spacer(Modifier.height(5.dp))
            val inactiveLabel = stringResource(R.string.assistant_inactive)
            val activeLabel = stringResource(R.string.assistant_active)
            SettingsSegmentedSelector(
                options = listOf(false, true),
                selected = errorLoggingEnabled,
                label = { if (it) activeLabel else inactiveLabel },
                controlHeight = SettingsDimensions.SelectorHeight,
                onSelected = { enabled ->
                    errorLoggingEnabled = enabled
                    settings.errorLoggingEnabled = enabled
                    errorLogMessage = ""
                },
            )
            Spacer(Modifier.height(8.dp))
            AssistantSettingsLabel(stringResource(R.string.assistant_error_logs))
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionButton(
                    stringResource(
                        R.string.assistant_export_errors_with_stats,
                        errorLogStats.fileCount,
                        formatStorageSize(errorLogStats.sizeBytes),
                    ),
                    SettingsDimensions.ActionHeight,
                    Modifier.weight(1f),
                ) {
                    onExportErrorLogs { exported ->
                        errorLogMessage = when {
                            exported == null -> ""
                            exported -> exportedTemplate
                            errorLogStats.fileCount == 0 -> noErrorsMessage
                            else -> exportFailedMessage
                        }
                    }
                }
                SettingsActionButton(
                    stringResource(R.string.assistant_clear_errors),
                    SettingsDimensions.ActionHeight,
                    Modifier.weight(1f),
                    destructive = true,
                ) {
                    onClearErrorLogs { deleted ->
                        errorLogStats = readErrorLogStats()
                        errorLogMessage = String.format(clearedTemplate, deleted)
                    }
                }
            }
            if (errorLogMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorLogMessage,
                    color = SettingsPalette.Accent,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            Column(
                Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SettingsSegmentedSelector(
                    options = AssistantCredentialKind.entries,
                    selected = credentialTab,
                    label = {
                        when (it) {
                            AssistantCredentialKind.OPENAI -> openAiCredentialLabel
                            AssistantCredentialKind.GEMINI -> geminiCredentialLabel
                            AssistantCredentialKind.PLACES -> placesCredentialLabel
                        }
                    },
                    controlHeight = SettingsDimensions.SelectorHeight,
                    onSelected = {
                        if (!isSaving) {
                            credentialTab = it
                            testRequestId++
                            message = ""
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                AssistantSettingsLabel(stringResource(R.string.assistant_model))
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(SettingsDimensions.FieldHeight)
                        .clip(RoundedCornerShape(7.dp))
                        .background(SettingsPalette.Control)
                        .border(1.dp, SettingsPalette.Border, RoundedCornerShape(7.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        credentialTab.model.ifBlank { placesService },
                        color = SettingsPalette.Text,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AssistantSettingsLabel(stringResource(R.string.assistant_api_key))
                Spacer(Modifier.height(5.dp))
                AssistantApiKeyField(
                    value = apiKeys.getValue(credentialTab),
                    onValueChange = { value ->
                        apiKeys[credentialTab] = value
                        testRequestId++
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(SettingsDimensions.FieldHeight),
                    enabled = !isSaving,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val deletedMessage = stringResource(R.string.assistant_keys_deleted)
                    val checkingMessage = stringResource(R.string.assistant_checking)
                    SettingsActionButton(
                        stringResource(R.string.assistant_save),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        enabled = !isSaving,
                    ) {
                        val key = apiKeys.getValue(credentialTab)
                        val requestId = ++testRequestId
                        message = ""
                        saveNotice = FloatingNotification(validatingMessage, FloatingNotificationTone.PROGRESS)
                        credentialTester.testConnection(credentialTab, key) { result ->
                            if (requestId != testRequestId) return@testConnection
                            if (result.successful) {
                                settings.saveApiKey(credentialTab, key)
                                onSaved()
                                saveNotice = FloatingNotification(keySavedMessage, FloatingNotificationTone.SUCCESS)
                            } else {
                                errorLogStats = readErrorLogStats()
                                saveNotice = FloatingNotification(result.message, FloatingNotificationTone.ERROR)
                            }
                        }
                    }
                    SettingsActionButton(
                        stringResource(R.string.assistant_delete_key),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        enabled = !isSaving,
                        destructive = true,
                    ) {
                        settings.deleteApiKey(credentialTab)
                        apiKeys[credentialTab] = ""
                        testRequestId++
                        message = deletedMessage
                        onSaved()
                    }
                    SettingsActionButton(
                        stringResource(R.string.assistant_test),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        enabled = !isSaving,
                    ) {
                        message = checkingMessage
                        val requestId = ++testRequestId
                        val onResult: (AssistantCredentialTestResult) -> Unit = {
                            if (requestId == testRequestId) {
                                message = it.message
                                errorLogStats = readErrorLogStats()
                            }
                        }
                        credentialTester.testConnection(
                            credentialTab,
                            apiKeys.getValue(credentialTab),
                            onResult,
                        )
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        color = SettingsPalette.Accent,
                        fontSize = 10.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        FloatingNotificationHost(
            notification = saveNotice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).zIndex(2f),
        )
    }
}

@Composable
private fun AssistantSettingsLabel(text: String) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text.uppercase(locale),
        color = SettingsPalette.MutedText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AssistantApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(color = SettingsPalette.Text, fontSize = 10.sp),
        cursorBrush = SolidColor(SettingsPalette.Accent),
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .border(
                1.dp,
                SettingsPalette.Border.copy(alpha = if (enabled) 1f else .55f),
                RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 10.dp),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                innerTextField()
            }
        },
    )
}
