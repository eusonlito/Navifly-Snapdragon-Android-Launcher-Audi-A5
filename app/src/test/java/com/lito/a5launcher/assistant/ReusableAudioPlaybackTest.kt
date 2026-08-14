package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReusableAudioPlaybackTest {
    @Test
    fun repeatRewindsTheLoadedTrackInsteadOfCreatingAnotherOne() {
        val handles = mutableListOf<FakePlaybackHandle>()
        val playback = ReusableAudioPlayback { _, _ ->
            FakePlaybackHandle().also(handles::add)
        }

        assertTrue(playback.play(byteArrayOf(1, 2), 24_000))
        assertTrue(playback.repeat())

        assertEquals(1, handles.size)
        assertEquals(1, handles.single().startCount)
        assertEquals(1, handles.single().repeatCount)
    }

    @Test
    fun loadingANewResponseReleasesThePreviousTrack() {
        val handles = mutableListOf<FakePlaybackHandle>()
        val playback = ReusableAudioPlayback { _, _ ->
            FakePlaybackHandle().also(handles::add)
        }

        playback.play(byteArrayOf(1, 2), 24_000)
        val first = handles.single()
        playback.play(byteArrayOf(3, 4), 24_000)

        assertEquals(2, handles.size)
        assertTrue(first.released)
    }

    @Test
    fun failedRepeatReleasesTheUnusableTrack() {
        val handle = FakePlaybackHandle(repeatResult = false)
        val playback = ReusableAudioPlayback { _, _ -> handle }

        assertTrue(playback.play(byteArrayOf(1, 2), 24_000))
        assertTrue(!playback.repeat())
        assertTrue(handle.released)
    }

    private class FakePlaybackHandle(
        private val repeatResult: Boolean = true,
    ) : PcmPlaybackHandle {
        var startCount = 0
        var repeatCount = 0
        var released = false

        override fun start(): Boolean {
            startCount++
            return true
        }

        override fun repeat(): Boolean {
            repeatCount++
            return repeatResult
        }

        override fun release() {
            released = true
        }
    }
}
