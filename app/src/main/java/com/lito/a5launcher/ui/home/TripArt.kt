package com.lito.a5launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal enum class TripGesture(val theme: HomeTheme, val durationMs: Int) {
    FISH(HomeTheme.SUBMARINE, 4600), SHOAL(HomeTheme.SUBMARINE, 5500), PERISCOPE(HomeTheme.SUBMARINE, 4800),
    ASTRONAUT(HomeTheme.MOON, 5200), UFO(HomeTheme.MOON, 4900), HEART(HomeTheme.MOON, 4500),
    LAUNCH(HomeTheme.GALAXY, 8000), AURORA(HomeTheme.GALAXY, 5700), ROVER(HomeTheme.GALAXY, 7300),
    PLANE(HomeTheme.ADVENTURE, 8900), BUTTERFLY(HomeTheme.ADVENTURE, 6500), STARS(HomeTheme.ADVENTURE, 5400),
}

internal class TripArt(theme: HomeTheme?) {
    val gestures = TripGesture.entries.filter { it.theme == theme }
    private val frames = gestures.associateWith { mutableFloatStateOf(0f) }
    operator fun get(gesture: TripGesture): Float = frames[gesture]?.floatValue ?: 0f

    suspend fun play(gesture: TripGesture) {
        val frame = frames.getValue(gesture)
        try {
            animate(0f, 1f, animationSpec = tween(gesture.durationMs, easing = LinearEasing)) { value, _ ->
                frame.floatValue = value
            }
        } finally {
            frame.floatValue = 0f
        }
    }
}

internal val LocalTripArt = staticCompositionLocalOf { TripArt(null) }

@Composable
internal fun rememberTripArt(theme: HomeTheme, canPlay: () -> Boolean): TripArt {
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
            while (true) {
                delay(due - SystemClock.uptimeMillis())
                val gesture = art.gestures[next]
                next = (next + 1) % art.gestures.size
                // An interruption restores the still art and preserves the next turn.
                val pause = Random.nextLong(120_000L, 240_001L)
                due = SystemClock.uptimeMillis() + pause
                art.play(gesture)
                due = SystemClock.uptimeMillis() + pause
            }
        }
    }
    return art
}

internal const val ART_PI = PI.toFloat()
internal fun artWave(p: Float): Float = sin(ART_PI * p).let { it * it }
internal fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
