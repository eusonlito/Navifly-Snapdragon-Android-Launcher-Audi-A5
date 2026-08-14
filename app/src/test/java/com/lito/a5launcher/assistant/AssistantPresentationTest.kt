package com.lito.a5launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantPresentationTest {
    @Test
    fun heardTextIsShownWithoutAnUnderstoodPrefix() {
        assertEquals("Abre Waze", normalizedAssistantText("  Abre Waze  "))
        assertEquals(null, normalizedAssistantText("   "))
    }
}
