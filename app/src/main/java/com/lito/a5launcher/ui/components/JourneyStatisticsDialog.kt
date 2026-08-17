package com.lito.a5launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.R
import java.util.Locale

@Composable
internal fun JourneyStatisticsDialog(
    title: String,
    statistics: JourneyStatisticsSnapshot,
    locale: Locale,
    onDismiss: () -> Unit,
) {
    val dialogWidth = minOf(LocalConfiguration.current.screenWidthDp.dp * .7f, 760.dp)

    AlertDialog(
        modifier = Modifier.width(dialogWidth),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatisticsRow(
                    firstLabel = stringResource(R.string.statistics_distance),
                    firstValue = stringResource(
                        R.string.statistics_distance_value,
                        formatOneDecimal(statistics.distanceKm, locale),
                    ),
                    secondLabel = stringResource(R.string.statistics_fuel_used),
                    secondValue = stringResource(
                        R.string.statistics_fuel_value,
                        formatFuelUsed(statistics.fuelUsedLitres, locale),
                    ),
                )
                StatisticsRow(
                    firstLabel = stringResource(R.string.statistics_elapsed_time),
                    firstValue = formatTripDuration(statistics.elapsedMs),
                    secondLabel = stringResource(R.string.statistics_moving_time),
                    secondValue = formatTripDuration(statistics.movingElapsedMs),
                )
                StatisticsRow(
                    firstLabel = stringResource(R.string.statistics_average_speed),
                    firstValue = speedValue(statistics.averageSpeedKmh, locale),
                    secondLabel = stringResource(R.string.statistics_maximum_speed),
                    secondValue = speedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                )
                StatisticsRow(
                    firstLabel = stringResource(R.string.consumption_calculated),
                    firstValue = consumptionValue(
                        statistics.calculatedConsumption,
                        statistics.distanceKm > 0.0,
                        locale,
                    ),
                    secondLabel = stringResource(R.string.consumption_simple),
                    secondValue = consumptionValue(
                        statistics.observedCanConsumption,
                        statistics.confirmedCanFuelUsedLitres > 0.0 &&
                            statistics.distanceKm > 0.0,
                        locale,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), color = OemCockpitTokens.Cyan)
            }
        },
        shape = RoundedCornerShape(10.dp),
        containerColor = OemCockpitTokens.DialHub,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun StatisticsRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        StatisticsValue(firstLabel, firstValue, Modifier.weight(1f))
        StatisticsValue(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatisticsValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = .68f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun consumptionValue(value: Double, available: Boolean, locale: Locale): String =
    if (available && value.isFinite()) {
        stringResource(R.string.consumption_value_format, formatOneDecimal(value, locale))
    } else {
        "—"
    }

@Composable
private fun speedValue(value: Double, locale: Locale): String = stringResource(
    R.string.statistics_speed_value,
    formatOneDecimal(value, locale),
)

internal fun formatFuelUsed(value: Double, locale: Locale): String =
    String.format(locale, "%.2f", value.takeIf { it.isFinite() } ?: 0.0)
