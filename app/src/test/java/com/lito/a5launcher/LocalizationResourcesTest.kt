package com.lito.a5launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcesTest {
    @Test
    fun spanishContainsEveryTranslatableBaseString() {
        val base = stringNames(File("src/main/res/values/strings.xml"))
        val spanish = stringNames(File("src/main/res/values-es/strings.xml"))
        assertEquals(base, spanish + setOf("app_name"))
    }

    @Test
    fun bothLanguagesExposeSystemAndDashboardLabels() {
        val required = setOf(
            "launcher_settings_tab_system",
            "language_spanish",
            "language_english",
            "dashboard_time",
            "dashboard_trip",
            "dashboard_consumption",
            "dashboard_refuel_distance",
            "dashboard_range",
            "dashboard_odometer",
        )
        assertTrue(stringNames(File("src/main/res/values/strings.xml")).containsAll(required))
        assertTrue(stringNames(File("src/main/res/values-es/strings.xml")).containsAll(required))
    }

    private fun stringNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                add(nodes.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }
}
