package com.lito.a5launcher.assistant

import com.lito.a5launcher.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantPresentationTest {
    @Test
    fun heardTextIsShownWithoutAnUnderstoodPrefix() {
        assertEquals("Abre Waze", normalizedAssistantText("  Abre Waze  "))
        assertEquals(null, normalizedAssistantText("   "))
    }

    @Test
    fun initializationHasVisibleStatusBeforeListeningStarts() {
        assertEquals(
            R.string.assistant_initializing,
            assistantStatusTextResource(AssistantState.Initializing),
        )
        assertEquals(
            R.string.assistant_listening,
            assistantStatusTextResource(AssistantState.Listening),
        )
    }
}
