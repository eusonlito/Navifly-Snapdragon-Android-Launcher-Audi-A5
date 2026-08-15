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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import java.util.Locale

@Composable
internal fun ConsumptionDetailsDialog(
    calculatedConsumption: Double,
    observedCanConsumption: Double,
    locale: Locale,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.consumption_details_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConsumptionDetailRow(
                    label = stringResource(R.string.consumption_calculated),
                    value = consumptionValue(calculatedConsumption, locale),
                )
                ConsumptionDetailRow(
                    label = stringResource(R.string.consumption_simple),
                    value = consumptionValue(observedCanConsumption, locale),
                )
                Text(
                    text = stringResource(R.string.consumption_simple_explanation),
                    color = Color.White.copy(alpha = .62f),
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.consumption_close),
                    color = OemCockpitTokens.Cyan,
                )
            }
        },
        shape = RoundedCornerShape(10.dp),
        containerColor = OemCockpitTokens.DialHub,
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

@Composable
private fun ConsumptionDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = .72f), fontSize = 15.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun consumptionValue(value: Double, locale: Locale): String =
    if (value.isFinite()) {
        stringResource(R.string.consumption_value_format, formatConsumptionNumber(value, locale))
    } else "—"

internal fun formatConsumptionNumber(value: Double, locale: Locale): String =
    String.format(locale, "%.1f", value)
