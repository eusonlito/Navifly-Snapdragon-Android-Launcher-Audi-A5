package com.lito.a5launcher.ui.components

import com.lito.a5launcher.ui.home.HomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSettingsPanelTest {
    @Test
    fun everyThemeHasItsOwnPreviewImage() {
        val previews = HomeTheme.entries.map(::themePreviewRes)
        assertEquals(HomeTheme.entries.size, previews.toSet().size)
        HomeTheme.entries.forEach { theme ->
            val file = File("src/main/res/drawable-nodpi/theme_preview_${theme.name.lowercase()}.webp")
            assertTrue("Missing preview for $theme", file.isFile)
        }
    }

}
