package com.lito.a5launcher.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import com.lito.a5launcher.functional.FunctionalEvent
import com.lito.a5launcher.functional.FunctionalEventCategory
import com.lito.a5launcher.functional.FunctionalEventLogAccess
import com.lito.a5launcher.functional.FunctionalEventSource
import com.lito.a5launcher.functional.FunctionalEventValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

internal data class FunctionalLogsActions(
    val setGlobalEnabled: (Boolean) -> Unit,
    val setCategoryEnabled: (FunctionalEventCategory, Boolean) -> Unit,
    val toggleExpanded: (Long) -> Unit,
    val loadMore: () -> Unit,
    val retry: () -> Unit,
    val exportAll: () -> Unit,
    val selectAllForDeletion: () -> Unit,
    val selectCategoryForDeletion: (FunctionalEventCategory) -> Unit,
)

@Composable
internal fun FunctionalLogsPanel(
    access: FunctionalEventLogAccess?,
    modifier: Modifier = Modifier,
) {
    if (access == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.functional_logs_loading),
                color = SettingsPalette.MutedText,
                fontSize = 11.sp,
            )
        }
        return
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val controller = remember(access) {
        FunctionalLogsController(JournalFunctionalLogsRepository(access.journal, access.settings))
    }
    val state by controller.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<FloatingNotification?>(null) }
    var pendingDeleteScope by remember { mutableStateOf<FunctionalLogsDeleteScope?>(null) }

    LaunchedEffect(controller) { controller.refresh() }
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(FLOATING_NOTIFICATION_VISIBLE_MS)
            notice = null
        }
    }

    val exportDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination != null) {
            scope.launch {
                val output = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(destination, "w")
                    }.getOrNull()
                }
                notice = if (output == null) {
                    FloatingNotification(
                        resources.getString(R.string.functional_logs_export_failed),
                        FloatingNotificationTone.ERROR,
                    )
                } else when (controller.export(output)) {
                    FunctionalLogsExportResult.SUCCESS -> FloatingNotification(
                        resources.getString(R.string.functional_logs_exported),
                        FloatingNotificationTone.SUCCESS,
                    )
                    FunctionalLogsExportResult.FAILED -> FloatingNotification(
                        resources.getString(R.string.functional_logs_export_failed),
                        FloatingNotificationTone.ERROR,
                    )
                    FunctionalLogsExportResult.CANCELLED,
                    FunctionalLogsExportResult.BUSY -> null
                }
            }
        }
    }

    pendingDeleteScope?.let { deleteScope ->
        AlertDialog(
            onDismissRequest = { pendingDeleteScope = null },
            title = { Text(stringResource(R.string.functional_logs_delete_confirm_title)) },
            text = { Text(deleteConfirmationMessage(deleteScope)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteScope = null
                        scope.launch {
                            val deleted = controller.delete(deleteScope)
                            notice = if (deleted != null) {
                                FloatingNotification(
                                    resources.getString(R.string.functional_logs_deleted, deleted),
                                    FloatingNotificationTone.SUCCESS,
                                )
                            } else {
                                FloatingNotification(
                                    resources.getString(R.string.functional_logs_delete_failed),
                                    FloatingNotificationTone.ERROR,
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.functional_logs_delete),
                        color = SettingsPalette.Danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteScope = null }) {
                    Text(stringResource(R.string.functional_logs_delete_cancel))
                }
            },
        )
    }

    val busy = state.operation != FunctionalLogsOperation.IDLE
    val actions = FunctionalLogsActions(
        setGlobalEnabled = { enabled ->
            scope.launch {
                if (!controller.setGlobalEnabled(enabled)) {
                    notice = FloatingNotification(
                        resources.getString(R.string.functional_logs_settings_failed),
                        FloatingNotificationTone.ERROR,
                    )
                }
            }
        },
        setCategoryEnabled = { category, enabled ->
            scope.launch {
                if (!controller.setCategoryEnabled(category, enabled)) {
                    notice = FloatingNotification(
                        resources.getString(R.string.functional_logs_settings_failed),
                        FloatingNotificationTone.ERROR,
                    )
                }
            }
        },
        toggleExpanded = controller::toggleExpanded,
        loadMore = { scope.launch { controller.loadNextPage() } },
        retry = { scope.launch { controller.retry() } },
        exportAll = {
            if (!busy) exportDestination.launch(
                resources.getString(R.string.functional_logs_export_file),
            )
        },
        selectAllForDeletion = {
            if (!busy) {
                pendingDeleteScope = FunctionalLogsDeleteScope.All
            }
        },
        selectCategoryForDeletion = { category ->
            if (!busy) {
                pendingDeleteScope = FunctionalLogsDeleteScope.Category(category)
            }
        },
    )

    Box(modifier.fillMaxSize()) {
        FunctionalLogsContent(state, actions)
        val progressNotice = when (state.operation) {
            FunctionalLogsOperation.EXPORTING -> FloatingNotification(
                stringResource(R.string.functional_logs_exporting),
                FloatingNotificationTone.PROGRESS,
            )
            FunctionalLogsOperation.DELETING -> FloatingNotification(
                stringResource(R.string.functional_logs_deleting),
                FloatingNotificationTone.PROGRESS,
            )
            FunctionalLogsOperation.IDLE -> notice
        }
        FloatingNotificationHost(
            notification = progressNotice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
        )
    }
}

@Composable
private fun FunctionalLogsContent(
    state: FunctionalLogsUiState,
    actions: FunctionalLogsActions,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FunctionalLogsControls(
            state = state,
            actions = actions,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        FunctionalLogsTimeline(
            state = state,
            actions = actions,
            modifier = Modifier.weight(2f).fillMaxHeight(),
        )
    }
}

@Composable
private fun FunctionalLogsControls(
    state: FunctionalLogsUiState,
    actions: FunctionalLogsActions,
    modifier: Modifier,
) {
    val busy = state.operation != FunctionalLogsOperation.IDLE
    val disabledLabel = stringResource(R.string.functional_logs_disabled)
    val enabledLabel = stringResource(R.string.functional_logs_enabled)
    SettingsCard(modifier) {
        SettingsSectionTitle(stringResource(R.string.functional_logs_capture))
        Spacer(Modifier.height(8.dp))
        SettingsSegmentedSelector(
            options = listOf(false, true),
            selected = state.settings.enabled,
            label = { enabled -> if (enabled) enabledLabel else disabledLabel },
            controlHeight = SettingsDimensions.SelectorHeight,
            enabled = !busy,
            onSelected = actions.setGlobalEnabled,
        )
        Spacer(Modifier.height(14.dp))
        SettingsSectionTitle(stringResource(R.string.functional_logs_categories))
        Spacer(Modifier.height(7.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            FunctionalEventCategory.entries.forEach { category ->
                val count = state.stats.categoryCounts[category] ?: 0L
                FunctionalLogsCategoryRow(
                    label = stringResource(FunctionalEventPresentation.categoryLabelRes(category)),
                    count = count,
                    checked = category in state.settings.categories,
                    enabled = !busy,
                    deleteEnabled = !busy && count > 0L,
                    onChecked = { actions.setCategoryEnabled(category, it) },
                    onDelete = { actions.selectCategoryForDeletion(category) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsActionButton(
                text = stringResource(R.string.functional_logs_export_all),
                controlHeight = SettingsDimensions.ActionHeight,
                modifier = Modifier.weight(1f),
                enabled = !busy && state.stats.validEvents > 0,
                onClick = actions.exportAll,
            )
            SettingsActionButton(
                text = stringResource(R.string.functional_logs_delete_all_button),
                controlHeight = SettingsDimensions.ActionHeight,
                modifier = Modifier.weight(1f),
                enabled = !busy && (state.stats.validEvents > 0 || state.stats.corruptLines > 0),
                destructive = true,
                onClick = actions.selectAllForDeletion,
            )
        }
    }
}

@Composable
private fun FunctionalLogsTimeline(
    state: FunctionalLogsUiState,
    actions: FunctionalLogsActions,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(state.events.size, state.endReached, state.pageLoading) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.events.isNotEmpty() && !state.endReached && !state.pageLoading &&
                lastVisible >= state.events.lastIndex - 4
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) actions.loadMore() }

    SettingsCard(modifier) {
        if (state.initialLoading && state.events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.functional_logs_loading),
                    color = SettingsPalette.MutedText,
                    fontSize = 11.sp,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.events, key = FunctionalEvent::sequence) { event ->
                    FunctionalLogEventRow(
                        event = event,
                        expanded = event.sequence in state.expandedSequences,
                        onClick = { actions.toggleExpanded(event.sequence) },
                    )
                }
                if (state.events.isEmpty() && state.loadError == null) {
                    item(key = "empty") { FunctionalLogsFooter(R.string.functional_logs_empty) }
                }
                if (state.pageLoading) {
                    item(key = "loading") {
                        FunctionalLogsFooter(R.string.functional_logs_loading_more)
                    }
                }
                if (state.loadError != null) {
                    item(key = "error") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                state.loadError,
                                color = SettingsPalette.Danger,
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.functional_logs_retry),
                                color = SettingsPalette.Accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = actions.retry).padding(7.dp),
                            )
                        }
                    }
                } else if (state.endReached && state.events.isNotEmpty()) {
                    item(key = "end") {
                        FunctionalLogsFooter(
                            if (state.displayLimitReached) {
                                R.string.functional_logs_display_limit
                            } else {
                                R.string.functional_logs_end
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionalLogEventRow(
    event: FunctionalEvent,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(event.capturedAtEpochMs, locale) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale)
            .format(Date(event.capturedAtEpochMs))
    }
    val summaryRes = FunctionalEventPresentation.summaryRes(event.type)
    val summary = if (summaryRes == R.string.functional_logs_summary_unknown) {
        stringResource(summaryRes, event.type.code)
    } else {
        stringResource(summaryRes)
    }
    val expansionDescription = stringResource(
        if (expanded) R.string.functional_logs_expanded else R.string.functional_logs_collapsed,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { stateDescription = expansionDescription }
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
            .border(
                width = 0.5.dp,
                color = SettingsPalette.Border.copy(alpha = .7f),
                shape = RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(FunctionalEventPresentation.categoryLabelRes(event.category)),
                color = SettingsPalette.Accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                summary,
                color = SettingsPalette.Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(date, color = SettingsPalette.MutedText, fontSize = 8.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            val source = stringResource(
                if (event.source == FunctionalEventSource.REPLAY) {
                    R.string.functional_logs_source_replay
                } else {
                    R.string.functional_logs_source_eventcenter
                },
            )
            Text(
                stringResource(
                    R.string.functional_logs_sequence,
                    event.sequence,
                    event.bootSession,
                    source,
                ),
                color = SettingsPalette.MutedText,
                fontSize = 8.sp,
            )
            event.context.toSortedMap().forEach { (key, value) ->
                val labelRes = FunctionalEventPresentation.contextLabelRes(key)
                val label = labelRes?.let { stringResource(it) }
                    ?: FunctionalEventPresentation.fallbackContextLabel(key)
                Text(
                    "$label: ${formatFunctionalEventValue(value, locale)}",
                    color = SettingsPalette.MutedText,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FunctionalLogsCategoryRow(
    label: String,
    count: Long,
    checked: Boolean,
    enabled: Boolean,
    deleteEnabled: Boolean,
    onChecked: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (checked) SettingsPalette.Accent.copy(alpha = .08f)
                    else SettingsPalette.Control.copy(alpha = .55f),
                )
                .border(
                    1.dp,
                    if (checked) SettingsPalette.Accent.copy(alpha = .55f)
                    else SettingsPalette.Border,
                    RoundedCornerShape(7.dp),
                )
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onChecked,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = SettingsPalette.Text.copy(alpha = if (enabled) 1f else .45f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                count.toString(),
                color = SettingsPalette.MutedText.copy(alpha = if (enabled) 1f else .45f),
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (checked) SettingsPalette.Accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (checked) SettingsPalette.Accent else SettingsPalette.MutedText,
                        RoundedCornerShape(5.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        SettingsDeleteIconButton(
            contentDescription = stringResource(
                R.string.functional_logs_delete_category,
                label,
            ),
            enabled = deleteEnabled,
            onClick = onDelete,
        )
    }
}

@Composable
private fun deleteConfirmationMessage(scope: FunctionalLogsDeleteScope): String = when (scope) {
    FunctionalLogsDeleteScope.All -> stringResource(R.string.functional_logs_delete_all_confirm)
    is FunctionalLogsDeleteScope.Category -> stringResource(
        R.string.functional_logs_delete_category_confirm,
        stringResource(FunctionalEventPresentation.categoryLabelRes(scope.category)),
    )
}

@Composable
private fun FunctionalLogsFooter(textRes: Int) {
    Text(
        stringResource(textRes),
        color = SettingsPalette.MutedText,
        fontSize = 9.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun formatFunctionalEventValue(value: FunctionalEventValue, locale: Locale): String =
    when (value) {
        is FunctionalEventValue.Text -> value.value
        is FunctionalEventValue.Integer -> NumberFormat.getIntegerInstance(locale).format(value.value)
        is FunctionalEventValue.Decimal -> NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 3
        }.format(value.value)
        is FunctionalEventValue.Flag -> stringResource(
            if (value.value) R.string.functional_logs_value_true else R.string.functional_logs_value_false,
        )
    }
