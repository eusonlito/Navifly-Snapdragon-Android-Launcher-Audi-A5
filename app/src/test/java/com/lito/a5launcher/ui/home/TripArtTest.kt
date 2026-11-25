package com.lito.a5launcher.ui.home

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripArtTest {
    @Test
    fun eachStoryPlaysItsActionsInNarrativeOrder() {
        for ((lead, response) in listOf(
            TripGesture.ROVER to TripGesture.LAUNCH,
            TripGesture.SATURN to TripGesture.AURORA,
            TripGesture.MOON_PLANET to TripGesture.ASTRONAUT,
            TripGesture.UFO to TripGesture.HEART,
            TripGesture.PERISCOPE to TripGesture.SEAWEED,
            TripGesture.FISH to TripGesture.SHOAL,
            TripGesture.BUTTERFLY to TripGesture.STARS,
            TripGesture.STARS to TripGesture.PLANE,
        )) {
            val story = TripArt(lead.theme).gestures
            assertEquals(response, story[story.indexOf(lead) + 1])
        }
    }

    @Test
    fun tapsPlayIndependentlyAndDoNotRestartAnActiveGesture() = runBlocking {
        val art = TripArt(HomeTheme.GALAXY)
        val clock = BroadcastFrameClock()
        val taps = launch(clock, start = CoroutineStart.UNDISPATCHED) { art.playTaps() }
        art.tap(TripGesture.AURORA)
        art.tap(TripGesture.ROVER)
        yield()
        yield()
        clock.sendFrame(1_000_000_000L)
        yield()
        clock.sendFrame(4_650_000_000L)
        yield()
        assertEquals(.5f, art[TripGesture.ROVER], .001f)
        assertTrue(art[TripGesture.AURORA] > .5f)
        art.tap(TripGesture.ROVER)
        yield()
        yield()
        clock.sendFrame(7_000_000_000L)
        yield()
        assertEquals(0f, art[TripGesture.AURORA], 0f)
        assertEquals(6f / 7.3f, art[TripGesture.ROVER], .001f)
        taps.cancelAndJoin()
        assertEquals(0f, art[TripGesture.ROVER], 0f)
    }

    @Test
    fun cancellingAPlayingGestureReturnsOnlyItsIllustrationToRest() = runBlocking {
        val art = TripArt(HomeTheme.GALAXY)
        val clock = BroadcastFrameClock()
        val animation = launch(clock, start = CoroutineStart.UNDISPATCHED) { art.play(TripGesture.ROVER) }
        clock.sendFrame(1_000_000_000L)
        yield()
        clock.sendFrame(4_650_000_000L)
        yield()
        assertEquals(.5f, art[TripGesture.ROVER], .001f)
        assertEquals(0f, art[TripGesture.LAUNCH], 0f)
        assertEquals(0f, art[TripGesture.AURORA], 0f)
        animation.cancelAndJoin()
        assertEquals(0f, art[TripGesture.ROVER], 0f)
        assertTrue(TripArt(HomeTheme.CLASSIC).gestures.isEmpty())
    }
}
