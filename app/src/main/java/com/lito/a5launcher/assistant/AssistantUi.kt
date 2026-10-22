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
import androidx.compose.material3.Icon
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
import com.lito.a5launcher.ui.components.SettingsActionButton
import com.lito.a5launcher.ui.components.SettingsCard
import com.lito.a5launcher.ui.components.SettingsDivider
import com.lito.a5launcher.ui.components.SettingsField
import com.lito.a5launcher.ui.components.SettingsIcons
import com.lito.a5launcher.ui.components.SettingsSectionTitle
import com.lito.a5launcher.ui.components.SettingsSegmentedSelector
import com.lito.a5launcher.ui.components.settingsFieldSurface
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
        is AssistantState.Offline -> state.message
        is AssistantState.Error -> state.message
        else -> assistantStatusTextResource(state)?.let { stringResource(it) }
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
        null -> normalizedAssistantText(heardText)
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

internal fun assistantStatusTextResource(state: AssistantState): Int? = when (state) {
    AssistantState.Disabled, AssistantState.Ready -> null
    AssistantState.Initializing -> R.string.assistant_initializing
    AssistantState.Listening -> R.string.assistant_listening
    AssistantState.Processing -> R.string.assistant_processing
    AssistantState.Speaking -> R.string.assistant_responding
    is AssistantState.Offline, is AssistantState.Error -> null
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
    val disabledLabel = stringResource(R.string.assistant_disabled)
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
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.CardGap),
        ) {
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.assistant_provider), SettingsIcons.Sparkles)
                Spacer(Modifier.height(10.dp))
                SettingsSegmentedSelector(
                    options = AssistantProvider.entries,
                    selected = provider,
                    label = {
                        when (it) {
                            AssistantProvider.DISABLED -> disabledLabel
                            AssistantProvider.OPENAI -> openAiCredentialLabel
                            AssistantProvider.GEMINI -> geminiCredentialLabel
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
                Spacer(Modifier.height(SettingsDimensions.SectionGap))
                SettingsDivider()
                Spacer(Modifier.height(SettingsDimensions.SectionGap))
                SettingsSectionTitle(stringResource(R.string.assistant_error_logging), SettingsIcons.Alert) {
                    Text(
                        stringResource(
                            R.string.settings_files_summary,
                            errorLogStats.fileCount,
                            formatStorageSize(errorLogStats.sizeBytes),
                        ),
                        color = SettingsPalette.MutedText,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.weight(1f))
                if (errorLogMessage.isNotBlank()) {
                    Text(
                        errorLogMessage,
                        color = SettingsPalette.Accent,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionButton(
                        stringResource(R.string.settings_download),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        icon = SettingsIcons.Download,
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
                        icon = SettingsIcons.Trash,
                    ) {
                        onClearErrorLogs { deleted ->
                            errorLogStats = readErrorLogStats()
                            errorLogMessage = String.format(clearedTemplate, deleted)
                        }
                    }
                }
            }
            SettingsCard(Modifier.weight(2f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.assistant_credentials), SettingsIcons.Key)
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(SettingsDimensions.SectionGap))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsField(
                        label = stringResource(R.string.assistant_model),
                        value = credentialTab.model.ifBlank { placesService },
                        modifier = Modifier.weight(1f),
                        icon = SettingsIcons.Chip,
                    )
                    AssistantApiKeyField(
                        label = stringResource(R.string.assistant_api_key),
                        value = apiKeys.getValue(credentialTab),
                        onValueChange = { value ->
                            apiKeys[credentialTab] = value
                            testRequestId++
                            message = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                    )
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        color = SettingsPalette.Accent,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val deletedMessage = stringResource(R.string.assistant_keys_deleted)
                    val checkingMessage = stringResource(R.string.assistant_checking)
                    SettingsActionButton(
                        stringResource(R.string.assistant_delete_key),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        enabled = !isSaving,
                        destructive = true,
                        icon = SettingsIcons.Trash,
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
                        icon = SettingsIcons.Activity,
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
                    SettingsActionButton(
                        stringResource(R.string.assistant_save),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        enabled = !isSaving,
                        prominent = true,
                        icon = SettingsIcons.Check,
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
private fun AssistantApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .height(SettingsDimensions.FieldHeight)
            .settingsFieldSurface(enabled)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SettingsIcons.Lock, contentDescription = null, tint = SettingsPalette.MutedText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = SettingsPalette.MutedText, fontSize = 10.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(
                    color = SettingsPalette.Text.copy(alpha = if (enabled) 1f else .5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(SettingsPalette.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.assistant_api_key_placeholder),
                                color = SettingsPalette.SubtleText,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}
