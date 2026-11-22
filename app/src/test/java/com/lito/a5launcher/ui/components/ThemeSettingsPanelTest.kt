package com.lito.a5launcher.ui.components

import com.lito.a5launcher.ui.home.HomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSettingsPanelTest {
    @Test
    fun everyThemeHasItsOwnPreviewImage() {
        val options = HomeTheme.entries + listOf(null)
        val previews = options.map(::themePreviewRes)
        assertEquals(options.size, previews.toSet().size)
        options.forEach { theme ->
            val file = File("src/main/res/drawable-nodpi/theme_preview_${theme?.name?.lowercase() ?: "random"}.webp")
            assertTrue("Missing preview for $theme", file.isFile)
        }
    }

}
