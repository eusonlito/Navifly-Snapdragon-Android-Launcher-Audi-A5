package com.lito.a5launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.R
import java.util.Locale

@Composable
internal fun JourneyStatisticsPanel(
    title: String,
    statistics: JourneyStatisticsSnapshot,
    locale: Locale,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentWidth = minOf(maxWidth * .4f, 430.dp)
        Column(
            modifier = Modifier.width(contentWidth),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.dialog_close),
                    color = OemCockpitTokens.Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

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
                secondLabel = stringResource(R.string.statistics_moving_average_speed),
                secondValue = speedValue(statistics.movingAverageSpeedKmh, locale),
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
                    statistics.confirmedCanFuelUsedLitres > 0.0 && statistics.distanceKm > 0.0,
                    locale,
                ),
            )
            StatisticsRow(
                firstLabel = stringResource(R.string.statistics_maximum_speed),
                firstValue = speedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                secondLabel = stringResource(R.string.statistics_fuel_spent),
                secondValue = statistics.observedFuelSpentLitres?.let { fuelSpent ->
                    stringResource(
                        R.string.statistics_fuel_value,
                        formatFuelUsed(fuelSpent, locale),
                    )
                } ?: "—",
            )
        }
    }
}

@Composable
private fun StatisticsRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = .1f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 1.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        StatisticsValue(firstLabel, firstValue, Modifier.weight(1f))
        StatisticsValue(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatisticsValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = .66f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 17.sp,
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
