package com.lito.a5launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsTabTest {
    @Test
    fun storedTabIsRestored() {
        LauncherSettingsTab.entries.forEach { tab ->
            assertEquals(tab, parseLauncherSettingsTab(tab.name))
        }
    }

    @Test
    fun missingOrUnknownTabFallsBackToTrips() {
        assertEquals(LauncherSettingsTab.TRIPS, parseLauncherSettingsTab(null))
        assertEquals(LauncherSettingsTab.TRIPS, parseLauncherSettingsTab("REMOVED_TAB"))
    }
}
