package com.lito.a5launcher.ui.components

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyHistoryKind
import com.lito.a5launcher.JourneyHistoryRecord
import com.lito.a5launcher.JourneyHistorySnapshot
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal enum class JourneyHistoryTypeFilter {
    ALL,
    TRIPS,
    PARTIALS,
}

internal const val CURRENT_TRIP_ID = "current-trip"
internal const val CURRENT_PARTIAL_ID = "current-partial"

internal data class JourneyHistoryItem(
    val id: String,
    val kind: JourneyHistoryKind,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val statistics: JourneyStatisticsSnapshot,
) {
    constructor(record: JourneyHistoryRecord) : this(
        id = record.id,
        kind = record.kind,
        startedAtEpochMs = record.startedAtEpochMs,
        endedAtEpochMs = record.endedAtEpochMs,
        statistics = record.statistics,
    )

    val isOpen: Boolean get() = endedAtEpochMs == null
}

internal fun buildJourneyHistoryItems(
    records: List<JourneyHistoryRecord>,
    tripStatistics: JourneyStatisticsSnapshot,
    partialStatistics: JourneyStatisticsSnapshot,
    nowEpochMs: Long,
): List<JourneyHistoryItem> = listOf(
    currentJourneyHistoryItem(
        id = CURRENT_TRIP_ID,
        kind = JourneyHistoryKind.TRIP,
        statistics = tripStatistics,
        nowEpochMs = nowEpochMs,
    ),
    currentJourneyHistoryItem(
        id = CURRENT_PARTIAL_ID,
        kind = JourneyHistoryKind.PARTIAL,
        statistics = partialStatistics,
        nowEpochMs = nowEpochMs,
    ),
) + records.map(::JourneyHistoryItem)

private fun currentJourneyHistoryItem(
    id: String,
    kind: JourneyHistoryKind,
    statistics: JourneyStatisticsSnapshot,
    nowEpochMs: Long,
) = JourneyHistoryItem(
    id = id,
    kind = kind,
    startedAtEpochMs = (nowEpochMs - statistics.elapsedMs.coerceAtLeast(0L))
        .coerceIn(1L, nowEpochMs.coerceAtLeast(1L)),
    endedAtEpochMs = null,
    statistics = statistics,
)

internal fun toggleJourneyHistoryExpansion(
    expandedIds: Set<String>,
    id: String,
): Set<String> = if (id in expandedIds) expandedIds - id else expandedIds + id

internal fun filterJourneyHistory(
    records: List<JourneyHistoryItem>,
    type: JourneyHistoryTypeFilter,
    fromEpochMs: Long?,
    untilEpochMs: Long?,
    nowEpochMs: Long,
): List<JourneyHistoryItem> = records.filter { record ->
    val typeMatches = when (type) {
        JourneyHistoryTypeFilter.ALL -> true
        JourneyHistoryTypeFilter.TRIPS -> record.kind == JourneyHistoryKind.TRIP
        JourneyHistoryTypeFilter.PARTIALS -> record.kind == JourneyHistoryKind.PARTIAL
    }
    typeMatches &&
        (fromEpochMs == null || (record.endedAtEpochMs ?: nowEpochMs) >= fromEpochMs) &&
        (untilEpochMs == null || record.startedAtEpochMs < untilEpochMs)
}

@Composable
internal fun JourneyHistoryPanel(
    snapshot: JourneyHistorySnapshot,
    tripStatistics: JourneyStatisticsSnapshot,
    partialStatistics: JourneyStatisticsSnapshot,
    onEnabledChanged: (Boolean) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    var typeFilter by rememberSaveable { mutableStateOf(JourneyHistoryTypeFilter.ALL) }
    var fromEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingExport by remember { mutableStateOf<JourneyHistoryExportRequest?>(null) }
    var exportNotice by remember { mutableStateOf<FloatingNotification?>(null) }
    val nowEpochMs = System.currentTimeMillis()

    val fromEpochMs = fromEpochDay?.let { epochDay ->
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val untilEpochMs = toEpochDay?.let { epochDay ->
        LocalDate.ofEpochDay(epochDay).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val allItems = remember(snapshot.records, tripStatistics, partialStatistics, nowEpochMs) {
        buildJourneyHistoryItems(
            records = snapshot.records,
            tripStatistics = tripStatistics,
            partialStatistics = partialStatistics,
            nowEpochMs = nowEpochMs,
        )
    }
    val filtered = remember(allItems, typeFilter, fromEpochMs, untilEpochMs, nowEpochMs) {
        filterJourneyHistory(allItems, typeFilter, fromEpochMs, untilEpochMs, nowEpochMs)
    }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    val filterLabels = mapOf(
        JourneyHistoryTypeFilter.ALL to stringResource(R.string.journey_history_filter_all),
        JourneyHistoryTypeFilter.TRIPS to stringResource(R.string.journey_history_filter_trips),
        JourneyHistoryTypeFilter.PARTIALS to stringResource(R.string.journey_history_filter_partials),
    )
    val enabledLabels = mapOf(
        false to stringResource(R.string.journey_history_disabled),
        true to stringResource(R.string.journey_history_enabled),
    )
    val exportFileDateFormatter = remember(zone) {
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(zone)
    }

    LaunchedEffect(exportNotice) {
        if (exportNotice != null) {
            kotlinx.coroutines.delay(FLOATING_NOTIFICATION_VISIBLE_MS)
            exportNotice = null
        }
    }

    val exportDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination ->
        val request = pendingExport
        pendingExport = null
        if (destination != null && request != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) {
                    writeJourneyHistoryDocument(
                        request = request,
                        openOutput = {
                            context.contentResolver.openOutputStream(destination, "w")
                        },
                        deleteFailedDocument = {
                            context.contentResolver.delete(destination, null, null)
                        },
                    )
                }
                exportNotice = FloatingNotification(
                    message = resources.getString(
                        if (exported) {
                            R.string.journey_history_exported
                        } else {
                            R.string.journey_history_export_failed
                        },
                    ),
                    tone = if (exported) {
                        FloatingNotificationTone.SUCCESS
                    } else {
                        FloatingNotificationTone.ERROR
                    },
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.journey_history_clear_title)) },
            text = { Text(stringResource(R.string.journey_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAll()
                    },
                ) {
                    Text(
                        stringResource(R.string.journey_history_clear_all),
                        color = SettingsPalette.Danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.journey_history_cancel))
                }
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.journey_history_recording))
                Spacer(Modifier.height(4.dp))
                SettingsSegmentedSelector(
                    options = listOf(false, true),
                    selected = snapshot.enabled,
                    label = enabledLabels::getValue,
                    controlHeight = SettingsDimensions.SelectorHeight,
                    onSelected = onEnabledChanged,
                )
    
                Spacer(Modifier.height(8.dp))
                SettingsSectionTitle(stringResource(R.string.journey_history_filter_type))
                Spacer(Modifier.height(4.dp))
                SettingsSegmentedSelector(
                    options = JourneyHistoryTypeFilter.entries,
                    selected = typeFilter,
                    label = filterLabels::getValue,
                    controlHeight = SettingsDimensions.SelectorHeight,
                    onSelected = { typeFilter = it },
                )
    
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsSectionTitle(stringResource(R.string.journey_history_filter_dates))
                    if (fromEpochDay != null || toEpochDay != null) {
                        Text(
                            text = stringResource(R.string.journey_history_clear_dates),
                            color = SettingsPalette.Accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                fromEpochDay = null
                                toEpochDay = null
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsActionButton(
                        text = fromEpochDay?.let {
                            stringResource(
                                R.string.journey_history_from_value,
                                LocalDate.ofEpochDay(it).format(dateFormatter),
                            )
                        } ?: stringResource(R.string.journey_history_from),
                        controlHeight = SettingsDimensions.FieldHeight,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialDate = fromEpochDay?.let(LocalDate::ofEpochDay),
                                onDateSelected = { selected ->
                                    fromEpochDay = selected.toEpochDay()
                                    if (toEpochDay != null && selected.toEpochDay() > toEpochDay!!) {
                                        toEpochDay = selected.toEpochDay()
                                    }
                                },
                            )
                        },
                    )
                    SettingsActionButton(
                        text = toEpochDay?.let {
                            stringResource(
                                R.string.journey_history_to_value,
                                LocalDate.ofEpochDay(it).format(dateFormatter),
                            )
                        } ?: stringResource(R.string.journey_history_to),
                        controlHeight = SettingsDimensions.FieldHeight,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialDate = toEpochDay?.let(LocalDate::ofEpochDay),
                                onDateSelected = { selected ->
                                    toEpochDay = selected.toEpochDay()
                                    if (fromEpochDay != null && selected.toEpochDay() < fromEpochDay!!) {
                                        fromEpochDay = selected.toEpochDay()
                                    }
                                },
                            )
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.journey_history_results,
                        filtered.size,
                        allItems.size,
                    ),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionButton(
                        text = stringResource(R.string.journey_history_export),
                        controlHeight = SettingsDimensions.FieldHeight,
                        modifier = Modifier.weight(1f),
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            val exportedAtEpochMs = System.currentTimeMillis()
                            pendingExport = JourneyHistoryExportRequest(
                                records = filtered,
                                typeFilter = typeFilter,
                                fromEpochMs = fromEpochMs,
                                untilEpochMs = untilEpochMs,
                                exportedAtEpochMs = exportedAtEpochMs,
                            )
                            exportDestination.launch(
                                resources.getString(
                                    R.string.journey_history_export_file,
                                    exportFileDateFormatter.format(
                                        Instant.ofEpochMilli(exportedAtEpochMs),
                                    ),
                                ),
                            )
                        },
                    )
                    SettingsActionButton(
                        text = stringResource(R.string.journey_history_clear_all),
                        controlHeight = SettingsDimensions.FieldHeight,
                        modifier = Modifier.weight(1f),
                        enabled = snapshot.records.isNotEmpty(),
                        destructive = true,
                        onClick = { showClearConfirmation = true },
                    )
                }
            }
    
            SettingsCard(Modifier.weight(2f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsSectionTitle(stringResource(R.string.journey_history_title))
                    Text(
                        text = filtered.size.toString(),
                        color = SettingsPalette.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.journey_history_empty),
                            color = SettingsPalette.MutedText,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(filtered, key = JourneyHistoryItem::id) { record ->
                            JourneyHistoryRecordRow(
                                record = record,
                                locale = locale,
                                expanded = record.id in expandedIds,
                                onToggleExpanded = {
                                    expandedIds = toggleJourneyHistoryExpansion(expandedIds, record.id)
                                },
                                onDelete = record.endedAtEpochMs?.let {
                                    { onDeleteRecord(record.id) }
                                },
                            )
                        }
                    }
                }
            }
        }
        FloatingNotificationHost(
            notification = exportNotice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
        )
    }
}

@Composable
private fun JourneyHistoryRecordRow(
    record: JourneyHistoryItem,
    locale: Locale,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val statistics = record.statistics
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                SettingsPalette.Control,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when {
                        record.isOpen && record.kind == JourneyHistoryKind.TRIP ->
                            R.string.journey_history_current_trip
                        record.isOpen -> R.string.journey_history_current_partial
                        record.kind == JourneyHistoryKind.TRIP -> R.string.journey_history_trip
                        else -> R.string.journey_history_partial
                    }
                ),
                color = SettingsPalette.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = record.endedAtEpochMs?.let { endedAt ->
                    stringResource(
                        R.string.journey_history_period,
                        formatRecordDate(record.startedAtEpochMs, dateTimeFormatter),
                        formatRecordDate(endedAt, dateTimeFormatter),
                    )
                } ?: stringResource(
                    R.string.journey_history_period,
                    formatRecordDate(record.startedAtEpochMs, dateTimeFormatter),
                    stringResource(R.string.journey_history_in_progress),
                ),
                color = SettingsPalette.MutedText,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.statistics_distance_value,
                    formatOneDecimal(statistics.distanceKm, locale),
                ),
                color = SettingsPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = formatTripDuration(statistics.elapsedMs),
                color = SettingsPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
            onDelete?.let {
                SettingsDeleteIconButton(
                    contentDescription = stringResource(R.string.journey_history_delete),
                    modifier = Modifier.padding(start = 5.dp),
                    onClick = it,
                )
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.journey_history_collapse
                    } else {
                        R.string.journey_history_expand
                    },
                ),
                tint = SettingsPalette.Accent,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (expanded) JourneyHistoryDetails(statistics, locale)
    }
}

@Composable
private fun JourneyHistoryDetails(
    statistics: JourneyStatisticsSnapshot,
    locale: Locale,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric(
                stringResource(R.string.statistics_distance),
                stringResource(R.string.statistics_distance_value, formatOneDecimal(statistics.distanceKm, locale)),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_elapsed_time),
                formatTripDuration(statistics.elapsedMs),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_moving_time),
                formatTripDuration(statistics.movingElapsedMs),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_average_speed),
                statisticsSpeedValue(statistics.averageSpeedKmh, locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_moving_average_speed),
                statisticsSpeedValue(statistics.movingAverageSpeedKmh, locale),
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric(
                stringResource(R.string.statistics_maximum_speed),
                statisticsSpeedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.consumption_calculated),
                statisticsConsumptionValue(
                    statistics.calculatedConsumption,
                    statistics.distanceKm > 0.0,
                    locale,
                ),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.consumption_simple),
                statisticsConsumptionValue(
                    statistics.observedCanConsumption,
                    statistics.confirmedCanFuelUsedLitres > 0.0 && statistics.distanceKm > 0.0,
                    locale,
                ),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_fuel_used),
                historyFuelValue(statistics.fuelUsedLitres, locale),
                Modifier.weight(1f),
            )
            HistoryMetric(
                stringResource(R.string.statistics_fuel_spent),
                statistics.observedFuelSpentLitres?.let { historyFuelValue(it, locale) } ?: "—",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = SettingsPalette.MutedText,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = SettingsPalette.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun historyFuelValue(value: Double, locale: Locale): String = stringResource(
    R.string.statistics_fuel_value,
    formatFuelUsed(value, locale),
)

private fun formatRecordDate(epochMs: Long, formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)

private fun showDatePicker(
    context: android.content.Context,
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = initialDate ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth,
    ).show()
}
