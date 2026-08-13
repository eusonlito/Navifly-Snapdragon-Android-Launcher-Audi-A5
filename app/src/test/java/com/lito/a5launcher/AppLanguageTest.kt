package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun spanishSystemStartsInSpanish() {
        assertEquals(AppLanguage.SPANISH, initialLanguage(Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun unsupportedSystemLanguageStartsInEnglish() {
        assertEquals(AppLanguage.ENGLISH, initialLanguage(Locale.forLanguageTag("gl-ES")))
    }
}
