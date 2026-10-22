package com.lito.a5launcher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin-stroke pictograms for the launcher settings. They share the 24-unit grid
 * and line weight of the cockpit header icons, so every settings screen keeps one
 * visual language without pulling the extended Material icon set into the APK.
 */
internal object SettingsIcons {
    val Route by lazy {
        strokeIcon(
            "Route",
            circle(6f, 19f, 3f),
            "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15",
            circle(18f, 5f, 3f),
        )
    }
    val Map by lazy { strokeIcon("Map", "M3 6.5l6-3 6 3 6-3v14l-6 3-6-3-6 3z", "M9 3.5v14", "M15 6.5v14") }
    val Sparkles by lazy {
        strokeIcon(
            "Sparkles",
            "M11 4l1.9 5.1L18 11l-5.1 1.9L11 18l-1.9-5.1L4 11l5.1-1.9z",
            "M19 2.5v4",
            "M17 4.5h4",
            "M19 17v4",
            "M17 19h4",
        )
    }
    val Document by lazy {
        strokeIcon(
            "Document",
            "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z",
            "M14 3v5h5",
            "M9 13h6",
            "M9 17h6",
            "M9 9h2",
        )
    }
    val Gear by lazy {
        strokeIcon(
            "Gear",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08" +
                "a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51" +
                "a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08" +
                "a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
                "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
                "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09" +
                "a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25" +
                "a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }
    val History by lazy {
        strokeIcon("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
    }
    val Layers by lazy { strokeIcon("Layers", "M12 2.5l9 5-9 5-9-5z", "M3 12l9 5 9-5", "M3 16.5l9 5 9-5") }
    val Calendar by lazy {
        strokeIcon(
            "Calendar",
            "M5 4.5h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z",
            "M16 2.5v4",
            "M8 2.5v4",
            "M3 10h18",
        )
    }
    val Download by lazy { strokeIcon("Download", "M12 3v12", "M7 10l5 5 5-5", "M5 21h14") }
    val Upload by lazy { strokeIcon("Upload", "M12 15V3", "M7 8l5-5 5 5", "M5 21h14") }
    val Trash by lazy {
        strokeIcon(
            "Trash",
            "M3 6h18",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
            "M10 11v6",
            "M14 11v6",
        )
    }
    val List by lazy {
        strokeIcon("List", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")
    }
    val Pin by lazy {
        strokeIcon(
            "Pin",
            "M20 10c0 4.99-5.54 10.19-7.4 11.8a1 1 0 0 1-1.2 0C9.54 20.19 4 14.99 4 10a8 8 0 0 1 16 0",
            circle(12f, 10f, 3f),
        )
    }
    val Clock by lazy { strokeIcon("Clock", circle(12f, 12f, 9f), "M12 7v5l3 2") }
    val ChevronDown by lazy { strokeIcon("ChevronDown", "M6 9l6 6 6-6") }
    val ChevronUp by lazy { strokeIcon("ChevronUp", "M6 15l6-6 6 6") }
    val Flag by lazy {
        strokeIcon("Flag", "M4 22V4", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1")
    }
    val Activity by lazy { strokeIcon("Activity", "M22 12h-4l-3 9L9 3l-3 9H2") }
    val Database by lazy {
        strokeIcon(
            "Database",
            "M3 5a9 3 0 1 0 18 0a9 3 0 1 0-18 0",
            "M3 5v14a9 3 0 0 0 18 0V5",
            "M3 12a9 3 0 0 0 18 0",
        )
    }
    val Key by lazy {
        strokeIcon("Key", circle(7.5f, 15.5f, 5.5f), "M21 2l-9.6 9.6", "M15.5 7.5l3 3L22 7l-3-3")
    }
    val Lock by lazy {
        strokeIcon(
            "Lock",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }
    val Chip by lazy {
        strokeIcon(
            "Chip",
            "M8 5h8a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
            "M9.5 9.5h5v5h-5z",
            "M9 2v3", "M15 2v3", "M9 19v3", "M15 19v3",
            "M2 9h3", "M2 15h3", "M19 9h3", "M19 15h3",
        )
    }
    val Alert by lazy {
        strokeIcon(
            "Alert",
            "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h16.9a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
            "M12 9v4",
            "M12 17h.01",
        )
    }
    val Check by lazy { strokeIcon("Check", "M20 6L9 17l-5-5") }
    val Refresh by lazy {
        strokeIcon(
            "Refresh",
            "M21 12a9 9 0 0 1-15.5 6.2L3 16",
            "M3 21v-5h5",
            "M3 12a9 9 0 0 1 15.5-6.2L21 8",
            "M21 3v5h-5",
        )
    }
    val Power by lazy { strokeIcon("Power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.8 0") }
    val Info by lazy { strokeIcon("Info", circle(12f, 12f, 9f), "M12 16v-4", "M12 8h.01") }
    val Globe by lazy {
        strokeIcon(
            "Globe",
            circle(12f, 12f, 9f),
            "M3 12h18",
            "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18",
        )
    }
    val Monitor by lazy {
        strokeIcon(
            "Monitor",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 21h8",
            "M12 17v4",
        )
    }
    val CloudDownload by lazy {
        strokeIcon(
            "CloudDownload",
            "M12 13v8",
            "M8 17l4 4 4-4",
            "M4.4 15.9A7 7 0 1 1 15.7 8h1.8a4.5 4.5 0 0 1 2.5 8.2",
        )
    }
    val Package by lazy {
        strokeIcon(
            "Package",
            "M21 16V8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.7l7 4" +
                "a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z",
            "M3.3 7L12 12l8.7-5",
            "M12 22V12",
            "M7.5 4.2l9 5.2",
        )
    }
    val Image by lazy {
        strokeIcon(
            "Image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            circle(9f, 9f, 2f),
            "M21 15l-3.1-3.1a2 2 0 0 0-2.8 0L6 21",
        )
    }
    val Tag by lazy {
        strokeIcon(
            "Tag",
            "M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.7 8.7a2.4 2.4 0 0 0 3.4 0" +
                "l6.6-6.6a2.4 2.4 0 0 0 0-3.4z",
            "M7.5 7.5h.01",
        )
    }
    val Wifi by lazy {
        strokeIcon(
            "Wifi",
            "M12 20h.01",
            "M2 8.8a15 15 0 0 1 20 0",
            "M5 12.9a10 10 0 0 1 14 0",
            "M8.5 16.4a5 5 0 0 1 7 0",
        )
    }
    val Crosshair by lazy {
        strokeIcon(
            "Crosshair",
            circle(12f, 12f, 7f),
            circle(12f, 12f, 2.5f),
            "M12 2v3", "M12 19v3", "M2 12h3", "M19 12h3",
        )
    }
    val Close by lazy { strokeIcon("Close", "M18 6L6 18", "M6 6l12 12") }
    val Record by lazy { strokeIcon("Record", circle(12f, 12f, 9f), circle(12f, 12f, 3.5f)) }
    val ListChecks by lazy {
        strokeIcon("ListChecks", "M3 7l2 2 4-4", "M3 17l2 2 4-4", "M13 6h8", "M13 12h8", "M13 18h8")
    }
    val Palette by lazy {
        strokeIcon(
            "Palette",
            "M12 22a10 10 0 1 1 10-10c0 2.8-2.2 4-4 4h-2.5a2 2 0 0 0-1.5 3.3c.4.5.5 1.2.2 1.8" +
                "a1.8 1.8 0 0 1-2.2.9z",
            "M7.5 10.5h.01",
            "M10.5 6.5h.01",
            "M15.5 7.5h.01",
        )
    }
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
