package com.lito.a5launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatisticsValue(
                        stringResource(R.string.statistics_distance),
                        stringResource(
                            R.string.statistics_distance_value,
                            formatOneDecimal(statistics.distanceKm, locale),
                        ),
                    )
                    StatisticsValue(
                        stringResource(R.string.statistics_elapsed_time),
                        formatTripDuration(statistics.elapsedMs),
                    )
                    StatisticsValue(
                        stringResource(R.string.statistics_moving_time),
                        formatTripDuration(statistics.movingElapsedMs),
                    )
                    StatisticsValue(
                        stringResource(R.string.statistics_average_speed),
                        speedValue(statistics.averageSpeedKmh, locale),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatisticsValue(
                        stringResource(R.string.statistics_maximum_speed),
                        speedValue(statistics.maximumSpeedKmh.toDouble(), locale),
                    )
                    StatisticsValue(
                        stringResource(R.string.consumption_calculated),
                        consumptionValue(
                            statistics.calculatedConsumption,
                            statistics.distanceKm > 0.0,
                            locale,
                        ),
                    )
                    StatisticsValue(
                        stringResource(R.string.consumption_simple),
                        consumptionValue(
                            statistics.observedCanConsumption,
                            statistics.confirmedCanFuelUsedLitres > 0.0 &&
                                statistics.distanceKm > 0.0,
                            locale,
                        ),
                    )
                    StatisticsValue(
                        stringResource(R.string.statistics_fuel_used),
                        stringResource(
                            R.string.statistics_fuel_value,
                            formatFuelUsed(statistics.fuelUsedLitres, locale),
                        ),
                    )
                }
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
    )
}

@Composable
private fun StatisticsValue(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = .68f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
