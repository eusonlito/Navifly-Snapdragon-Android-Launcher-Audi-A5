package com.lito.a5launcher.ui.home

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.lito.a5launcher.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal enum class TripGesture(val theme: HomeTheme, val durationMs: Int, @StringRes val touchLabel: Int) {
    FISH(HomeTheme.SUBMARINE, 4600, R.string.trip_art_fish),
    SHOAL(HomeTheme.SUBMARINE, 5500, R.string.trip_art_shoal),
    PERISCOPE(HomeTheme.SUBMARINE, 4800, R.string.trip_art_periscope),
    SEAWEED(HomeTheme.SUBMARINE, 6200, R.string.trip_art_seaweed),
    ASTRONAUT(HomeTheme.MOON, 5200, R.string.trip_art_astronaut),
    UFO(HomeTheme.MOON, 4900, R.string.trip_art_ufo),
    HEART(HomeTheme.MOON, 4500, R.string.trip_art_robot),
    MOON_LAUNCH(HomeTheme.MOON, 7600, R.string.trip_art_launch),
    MOON_PLANET(HomeTheme.MOON, 6900, R.string.trip_art_moon_planet),
    LAUNCH(HomeTheme.GALAXY, 8000, R.string.trip_art_launch),
    AURORA(HomeTheme.GALAXY, 5700, R.string.trip_art_aurora),
    ROVER(HomeTheme.GALAXY, 7300, R.string.trip_art_rover),
    SATURN(HomeTheme.GALAXY, 7200, R.string.trip_art_saturn),
    PLANE(HomeTheme.ADVENTURE, 8900, R.string.trip_art_plane),
    BUTTERFLY(HomeTheme.ADVENTURE, 6500, R.string.trip_art_butterfly),
    STARS(HomeTheme.ADVENTURE, 5400, R.string.trip_art_stars),
}

internal class TripArt(theme: HomeTheme?) {
    val gestures = TripGesture.entries.filter { it.theme == theme }
    private val frames = gestures.associateWith { mutableFloatStateOf(0f) }
    private val taps = MutableSharedFlow<TripGesture>(extraBufferCapacity = gestures.size.coerceAtLeast(1))
    private val playing = mutableSetOf<TripGesture>()

    operator fun get(gesture: TripGesture): Float = frames[gesture]?.floatValue ?: 0f

    fun tap(gesture: TripGesture) {
        taps.tryEmit(gesture)
    }

    suspend fun playTaps(): Unit = coroutineScope {
        taps.collect { gesture -> launch { play(gesture) } }
    }

    suspend fun play(gesture: TripGesture) {
        val frame = frames[gesture] ?: return
        if (!playing.add(gesture)) return
        try {
            animate(0f, 1f, animationSpec = tween(gesture.durationMs, easing = LinearEasing)) { value, _ ->
                frame.floatValue = value
            }
        } finally {
            frame.floatValue = 0f
            playing.remove(gesture)
        }
    }
}

internal val LocalTripArt = staticCompositionLocalOf { TripArt(null) }

@Composable
internal fun rememberTripArt(theme: HomeTheme, speed: State<Int>, canPlay: () -> Boolean): TripArt {
    val art = remember(theme) { TripArt(theme) }
    if (art.gestures.isEmpty()) return art
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val allowed by rememberUpdatedState(canPlay)
    LaunchedEffect(art, lifecycle) {
        val durationScale = coroutineContext[MotionDurationScale]
        var next = 0
        var due = SystemClock.uptimeMillis() + 2000L
        snapshotFlow {
            lifecycle.value.isAtLeast(Lifecycle.State.RESUMED) && allowed() && (durationScale?.scaleFactor ?: 1f) > 0f
        }.collectLatest { enabled ->
            if (!enabled) return@collectLatest
            coroutineScope {
                launch { art.playTaps() }
                snapshotFlow { speed.value > 0 }.collectLatest automatic@{ moving ->
                    if (!moving) return@automatic
                    while (true) {
                        delay(due - SystemClock.uptimeMillis())
                        val gesture = art.gestures[next]
                        next = (next + 1) % art.gestures.size
                        // An interruption restores the still art and preserves the next turn.
                        val pause = Random.nextLong(25_000L, 50_001L)
                        due = SystemClock.uptimeMillis() + pause
                        art.play(gesture)
                        due = SystemClock.uptimeMillis() + pause
                    }
                }
            }
        }
    }
    return art
}

@Composable
internal fun BoxScope.TripTapTarget(gesture: TripGesture, bounds: (Size) -> Rect) {
    val art = LocalTripArt.current
    val label = stringResource(gesture.touchLabel)
    Layout(
        modifier = Modifier.matchParentSize(),
        content = {
            Box(
                Modifier
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { art.tap(gesture) }
                    .semantics { contentDescription = label },
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rect = bounds(Size(width.toFloat(), height.toFloat()))
            .intersect(Rect(0f, 0f, width.toFloat(), height.toFloat()))
        val minimum = 48.dp.roundToPx()
        val targetWidth = rect.width.roundToInt().coerceIn(minimum.coerceAtMost(width), width)
        val targetHeight = rect.height.roundToInt().coerceIn(minimum.coerceAtMost(height), height)
        val target = measurables.single().measure(Constraints.fixed(targetWidth, targetHeight))
        layout(width, height) {
            if (rect.width > 0f && rect.height > 0f) {
                val x = (rect.center.x - targetWidth / 2f).roundToInt().coerceIn(0, width - targetWidth)
                val y = (rect.center.y - targetHeight / 2f).roundToInt().coerceIn(0, height - targetHeight)
                target.place(x, y)
            }
        }
    }
}

internal const val ART_PI = PI.toFloat()
internal fun artWave(p: Float): Float = sin(ART_PI * p).let { it * it }
internal fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
