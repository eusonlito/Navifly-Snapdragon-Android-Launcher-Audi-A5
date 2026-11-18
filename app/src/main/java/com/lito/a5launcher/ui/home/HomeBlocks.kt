package com.lito.a5launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.R
import com.lito.a5launcher.model.DoorStatus
import com.lito.a5launcher.ui.components.FooterBlockItem
import com.lito.a5launcher.ui.components.FuelSegmentTone
import com.lito.a5launcher.ui.components.OemCockpitTokens
import com.lito.a5launcher.ui.components.StatisticsPanelScope
import com.lito.a5launcher.ui.components.formatOneDecimal
import com.lito.a5launcher.ui.components.formatTripDuration
import com.lito.a5launcher.ui.components.fuelSegmentTone
import com.lito.a5launcher.ui.components.fuelSegments
import com.lito.a5launcher.ui.theme.SLineRed
import java.text.NumberFormat
import java.util.Locale

internal const val RANGE_WARNING_KM = 79

internal fun formatDashboardInteger(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value)

/** Statistics panel opened by tapping a block, as in the original footer. */
internal fun blockStatisticsScope(item: FooterBlockItem): StatisticsPanelScope? = when (item) {
    FooterBlockItem.TIME, FooterBlockItem.TRIP, FooterBlockItem.CONSUMPTION -> StatisticsPanelScope.TRIP
    FooterBlockItem.REFUEL_DISTANCE -> StatisticsPanelScope.PARTIAL
    FooterBlockItem.ODOMETER -> StatisticsPanelScope.TOTAL
    FooterBlockItem.RANGE, FooterBlockItem.FUEL, FooterBlockItem.WITNESSES -> null
}

internal data class BlockReading(
    val value: String,
    val unit: String?,
    val warning: Boolean,
)

/** Text value of a numeric block; FUEL and WITNESSES are drawn graphically. */
internal fun blockReading(
    item: FooterBlockItem,
    trip: JourneyStatisticsSnapshot,
    partial: JourneyStatisticsSnapshot,
    range: Int,
    mileage: Int,
    locale: Locale,
): BlockReading? = when (item) {
    FooterBlockItem.TIME -> BlockReading(formatTripDuration(trip.elapsedMs), null, false)
    FooterBlockItem.TRIP -> BlockReading(formatOneDecimal(trip.distanceKm, locale), "km", false)
    FooterBlockItem.CONSUMPTION -> BlockReading(
        if (trip.calculatedConsumption.isFinite()) {
            String.format(locale, "%.1f", trip.calculatedConsumption)
        } else "—",
        "L/100",
        false,
    )
    FooterBlockItem.REFUEL_DISTANCE -> BlockReading(formatOneDecimal(partial.distanceKm, locale), "km", false)
    FooterBlockItem.RANGE -> BlockReading(
        if (range > 0) formatDashboardInteger(range, locale) else "—",
        "km",
        range in 1..RANGE_WARNING_KM,
    )
    FooterBlockItem.ODOMETER -> BlockReading(
        if (mileage > 0) formatDashboardInteger(mileage, locale) else "—",
        "km",
        false,
    )
    FooterBlockItem.FUEL, FooterBlockItem.WITNESSES -> null
}

internal fun blockLabelRes(item: FooterBlockItem): Int = when (item) {
    FooterBlockItem.TIME -> R.string.dashboard_time
    FooterBlockItem.TRIP -> R.string.dashboard_trip
    FooterBlockItem.CONSUMPTION -> R.string.dashboard_consumption
    FooterBlockItem.REFUEL_DISTANCE -> R.string.dashboard_refuel_distance
    FooterBlockItem.RANGE -> R.string.dashboard_range
    FooterBlockItem.FUEL -> R.string.dashboard_fuel
    FooterBlockItem.WITNESSES -> R.string.dashboard_witnesses
    FooterBlockItem.ODOMETER -> R.string.dashboard_odometer
}

/** Reads only the inputs of [item], so other telemetry does not recompose it. */
@Composable
internal fun rememberBlockReading(item: FooterBlockItem, state: HomeState): BlockReading? {
    val locale = LocalConfiguration.current.locales[0]
    return when (item) {
        FooterBlockItem.TIME, FooterBlockItem.TRIP, FooterBlockItem.CONSUMPTION ->
            blockReading(item, state.trip.value, state.partial.value, 0, 0, locale)
        FooterBlockItem.REFUEL_DISTANCE ->
            blockReading(item, state.trip.value, state.partial.value, 0, 0, locale)
        FooterBlockItem.RANGE ->
            blockReading(item, state.trip.value, state.partial.value, state.range.value, 0, locale)
        FooterBlockItem.ODOMETER ->
            blockReading(item, state.trip.value, state.partial.value, 0, state.mileage.value, locale)
        FooterBlockItem.FUEL, FooterBlockItem.WITNESSES -> null
    }
}

@Composable
internal fun MiniValue(
    label: String,
    value: String,
    warning: Boolean,
    labelSize: TextUnit,
    valueSize: TextUnit,
) {
    val tokens = LocalHomeTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            color = tokens.label,
            fontSize = labelSize,
            lineHeight = labelSize,
            fontWeight = FontWeight.Bold,
            fontFamily = tokens.labelFamily,
        )
        Text(
            value,
            color = if (warning) tokens.warning else tokens.content,
            fontSize = valueSize,
            lineHeight = valueSize,
            fontWeight = tokens.numberWeight,
            fontFamily = tokens.numberFamily,
        )
    }
}

/** Fuel level colours are the classic ones in every theme, so a level always reads the same. */
internal val FuelNormalColor = OemCockpitTokens.Cyan
internal val FuelLowColor = Color(0xFFFFC400)
internal val FuelReserveColor = SLineRed

internal fun fuelContentColor(tone: FuelSegmentTone, tokens: HomeTokens): Color = when (tone) {
    FuelSegmentTone.RED -> FuelReserveColor
    FuelSegmentTone.YELLOW -> FuelLowColor
    FuelSegmentTone.NORMAL -> tokens.content
}

internal fun fuelSegmentColor(active: Boolean, tone: FuelSegmentTone, tokens: HomeTokens): Color = when {
    !active -> tokens.fuelInactive
    tone == FuelSegmentTone.RED -> FuelReserveColor
    tone == FuelSegmentTone.YELLOW -> FuelLowColor
    else -> FuelNormalColor
}

/** Original footer fuel block: pump, ten segments and litres. */
@Composable
internal fun FuelValue(
    fuel: Int,
    valueSize: TextUnit,
) {
    val tokens = LocalHomeTokens.current
    val activeSegments = fuelSegments(fuel)
    val segmentTone = fuelSegmentTone(activeSegments)
    val contentColor = fuelContentColor(segmentTone, tokens)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FuelPumpIcon(
                color = contentColor.copy(alpha = if (segmentTone == FuelSegmentTone.NORMAL) .62f else 1f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(10) { index ->
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(fuelSegmentColor(index < activeSegments, segmentTone, tokens))
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                fuel.toString(),
                color = contentColor,
                fontSize = valueSize,
                fontWeight = tokens.numberWeight,
                fontFamily = tokens.numberFamily,
            )
        }
    }
}

@Composable
internal fun FuelPumpIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.drawWithCache {
        val stroke = Stroke(width = size.width * .085f)
        val hose = Path().apply {
            moveTo(size.width * .65f, size.height * .25f)
            quadraticTo(size.width * .86f, size.height * .30f, size.width * .82f, size.height * .55f)
            lineTo(size.width * .82f, size.height * .68f)
        }
        onDrawBehind {
            drawRect(
                color,
                topLeft = Offset(size.width * .17f, size.height * .12f),
                size = Size(size.width * .48f, size.height * .68f),
                style = stroke,
            )
            drawLine(color, Offset(size.width * .12f, size.height * .82f), Offset(size.width * .72f, size.height * .82f), stroke.width)
            drawLine(color, Offset(size.width * .25f, size.height * .30f), Offset(size.width * .57f, size.height * .30f), stroke.width)
            drawPath(hose, color, style = stroke)
        }
    }) { }
}

internal enum class WitnessType { Seatbelt, Parking, Lights }
private enum class DoorRegion { Body, FrontLeft, FrontRight, RearLeft, RearRight }

/** Door, seatbelt, parking brake and lights witnesses; [scale] 1 keeps the original 30/34/38 dp. */
@Composable
internal fun WitnessGroup(
    state: HomeState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    spacing: Dp = 20.dp,
    vertical: Boolean = false,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(spacing),
) {
    val content: @Composable () -> Unit = {
        DoorStatusWitness(state.doors.value, scale)
        StatusWitness(state.seatbeltAlert.value, WitnessType.Seatbelt, scale)
        StatusWitness(state.parkingBrake.value, WitnessType.Parking, scale)
        StatusWitness(state.lightsActive.value, WitnessType.Lights, scale)
    }
    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) { content() }
    } else {
        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = arrangement,
        ) { content() }
    }
}

@Composable
internal fun DoorStatusWitness(doors: DoorStatus, scale: Float = 1f) {
    val tokens = LocalHomeTokens.current
    val alertColor = tokens.witnessAlert
    val inactiveColor = tokens.witnessInactive
    val anyOpen = doors.driverOpen || doors.passengerOpen ||
        doors.rearLeftOpen || doors.rearRightOpen || doors.hoodOpen || doors.trunkOpen
    val size = 30.dp * scale
    val pulse = rememberWitnessPulse(anyOpen)
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        WitnessPulseRing(pulse, alertColor)
        // Exact complete SVG supplied for this witness. It is the inactive
        // base, so no independently scaled door can alter its silhouette.
        Icon(
            painter = painterResource(R.drawable.ic_witness_door),
            contentDescription = null,
            tint = inactiveColor,
            modifier = Modifier.size(size),
        )
        // Every alert layer uses the base silhouette, including the body.
        if (anyOpen) DoorLayer(DoorRegion.Body, alertColor, size)
        // The top-down SVG is mirrored relative to the CAN left/right naming.
        if (doors.driverOpen) DoorLayer(DoorRegion.FrontRight, alertColor, size)
        if (doors.passengerOpen) DoorLayer(DoorRegion.FrontLeft, alertColor, size)
        if (doors.rearLeftOpen) DoorLayer(DoorRegion.RearRight, alertColor, size)
        if (doors.rearRightOpen) DoorLayer(DoorRegion.RearLeft, alertColor, size)
    }
}

@Composable
private fun DoorLayer(region: DoorRegion, color: Color, size: Dp) {
    Icon(
        painter = painterResource(R.drawable.ic_witness_door),
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(size)
            .drawWithContent doorLayer@{
                // Coordinates come directly from the 717 × 717 VectorDrawable.
                // Each active region is therefore a clipped portion of the exact
                // same path used by the inactive base—never a scaled copy.
                val bodyLeft = this.size.width * (171f / 717f)
                val bodyRight = this.size.width * (546f / 717f)
                val splitY = this.size.height * 0.55f
                when (region) {
                    DoorRegion.Body -> clipRect(bodyLeft, 0f, bodyRight, this.size.height) { this@doorLayer.drawContent() }
                    DoorRegion.FrontLeft -> clipRect(0f, 0f, bodyLeft, splitY) { this@doorLayer.drawContent() }
                    DoorRegion.FrontRight -> clipRect(bodyRight, 0f, this.size.width, splitY) { this@doorLayer.drawContent() }
                    DoorRegion.RearLeft -> clipRect(0f, splitY, bodyLeft, this.size.height) { this@doorLayer.drawContent() }
                    DoorRegion.RearRight -> clipRect(bodyRight, splitY, this.size.width, this.size.height) { this@doorLayer.drawContent() }
                }
            },
    )
}

@Composable
internal fun StatusWitness(active: Boolean, type: WitnessType, scale: Float = 1f) {
    val tokens = LocalHomeTokens.current
    val color = when {
        !active -> tokens.witnessInactive
        type == WitnessType.Lights -> tokens.witnessLights
        else -> tokens.witnessAlert
    }
    val icon = when (type) {
        WitnessType.Seatbelt -> R.drawable.ic_witness_seatbelt
        WitnessType.Parking -> R.drawable.ic_witness_parking_brake
        WitnessType.Lights -> R.drawable.ic_witness_lights
    }
    val iconSize = (if (type == WitnessType.Seatbelt) 34.dp else 38.dp) * scale
    val pulse = rememberWitnessPulse(active)
    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
        WitnessPulseRing(pulse, color)
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun rememberWitnessPulse(active: Boolean): Animatable<Float, AnimationVector1D> {
    val pulse = remember { Animatable(0f) }
    var previousActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        val shouldPulse = active && !previousActive
        previousActive = active
        if (shouldPulse) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(220))
        } else {
            pulse.snapTo(0f)
        }
    }
    return pulse
}

@Composable
private fun BoxScope.WitnessPulseRing(
    progress: Animatable<Float, AnimationVector1D>,
    color: Color,
) {
    Canvas(Modifier.matchParentSize().graphicsLayer {
        val value = progress.value
        scaleX = 1f + (1f - value) * .18f
        scaleY = 1f + (1f - value) * .18f
        alpha = value
    }) {
        drawCircle(
            color = color.copy(alpha = .42f),
            radius = size.minDimension * .49f,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

/** Uppercase label + value with optional unit, styled by the active tokens. */
@Composable
internal fun BlockLabel(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color? = null) {
    val tokens = LocalHomeTokens.current
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]),
        color = color ?: tokens.label,
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.SemiBold,
        fontFamily = tokens.labelFamily,
        maxLines = 1,
        style = TightTextStyle,
        modifier = modifier.capHeightBounds(size),
    )
}

@Composable
internal fun blockLabel(item: FooterBlockItem): String = stringResource(blockLabelRes(item))
