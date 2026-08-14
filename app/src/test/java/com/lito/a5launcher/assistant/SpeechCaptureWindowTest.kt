package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechCaptureWindowTest {
    @Test
    fun stopsWithoutErrorWhenNoVoiceArrivesWithinThreeSeconds() {
        val window = SpeechCaptureWindow()

        assertEquals(SpeechCaptureDecision.CONTINUE, window.onAudioLevel(0, 2_999L))
        assertEquals(SpeechCaptureDecision.NO_SPEECH, window.onAudioLevel(0, 3_000L))
    }

    @Test
    fun completesAfterSpeechFollowedBySilence() {
        val window = SpeechCaptureWindow()

        assertEquals(SpeechCaptureDecision.CONTINUE, window.onAudioLevel(600, 2_900L))
        assertEquals(SpeechCaptureDecision.CONTINUE, window.onAudioLevel(0, 3_000L))
        assertEquals(SpeechCaptureDecision.CONTINUE, window.onAudioLevel(0, 4_999L))
        assertEquals(SpeechCaptureDecision.SPEECH_COMPLETE, window.onAudioLevel(0, 5_000L))
    }

    @Test
    fun speechBeginningAtThreeSecondsIsAccepted() {
        val window = SpeechCaptureWindow()

        assertEquals(SpeechCaptureDecision.CONTINUE, window.onAudioLevel(600, 3_000L))
    }
}
