package com.lito.a5launcher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun poiCategoryLabel(category: String): String = when (category) {
    "speed-camera", "radar-fijo" -> stringResource(R.string.poi_alert_fixed)
    "average-speed-camera" -> stringResource(R.string.poi_alert_section)
    "radar-semaforo" -> stringResource(R.string.poi_alert_traffic_light)
    "radar-stop" -> stringResource(R.string.poi_alert_stop)
    "radar-tramo-inicio" -> stringResource(R.string.poi_alert_section_start)
    "radar-tramo-fin" -> stringResource(R.string.poi_alert_section_end)
    else -> category.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.titlecase() }
}

@Composable
internal fun PoiCategorySettingsPanel(
    snapshot: PoiSnapshot,
    onDistanceChanged: suspend (String, Int) -> Boolean,
    onBack: () -> Unit,
) {
    val categories = remember(snapshot.categoryCatalog) { snapshot.categoryCatalog?.styles.orEmpty().toSortedMap().toList() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsActionButton(stringResource(R.string.launcher_settings_tab_map), 40.dp, icon = SettingsIcons.Back, onClick = onBack)
            Text(stringResource(R.string.poi_alert_distances), color = SettingsPalette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (categories.isEmpty()) {
            SettingsCard(Modifier.fillMaxSize()) {
                SettingsEmptyState(SettingsIcons.Tag, stringResource(R.string.poi_no_categories), Modifier.fillMaxSize())
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(categories, key = { it.first }) { (category, style) ->
                    PoiCategoryDistanceRow(category, style, snapshot, onDistanceChanged)
                }
            }
        }
    }
}

@Composable
private fun PoiCategoryDistanceRow(
    category: String,
    style: PoiCategoryStyle,
    snapshot: PoiSnapshot,
    onDistanceChanged: suspend (String, Int) -> Boolean,
) {
    val storedMetres = snapshot.alertDistanceMetres(category)
    var metres by remember(category, storedMetres) { mutableIntStateOf(storedMetres) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val label = poiCategoryLabel(category)
    val icon = snapshot.icons.firstOrNull { it.code == style.icon }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, icon) {
        value = withContext(Dispatchers.IO) { icon?.file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }
    }
    fun save(value: Int) {
        metres = value
        saving = true
        scope.launch {
            try {
                if (!onDistanceChanged(category, value)) metres = storedMetres
            } finally {
                saving = false
            }
        }
    }
    SettingsCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val image = bitmap
            if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(40.dp))
            else Icon(SettingsIcons.Pin, contentDescription = null, tint = SettingsPalette.Accent, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, color = SettingsPalette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pulseColor = remember(style.pulseColor) { Color(android.graphics.Color.parseColor(style.pulseColor)) }
                    Box(Modifier.size(8.dp).background(if (style.pulseEnabled) pulseColor else SettingsPalette.SubtleText, CircleShape))
                    Text(stringResource(if (style.pulseEnabled) R.string.poi_pulse_enabled else R.string.poi_pulse_disabled), color = SettingsPalette.MutedText, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(2f)) {
                Text(String.format(locale, "%.1f km", metres / 1_000.0), color = SettingsPalette.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val decrease = stringResource(R.string.poi_distance_decrease, label)
                    val increase = stringResource(R.string.poi_distance_increase, label)
                    SettingsActionButton("−", 48.dp, modifier = Modifier.width(48.dp).semantics { contentDescription = decrease }, enabled = !saving && metres > 100, fontSize = 22.sp) { save(metres - 100) }
                    Slider(
                        value = metres.toFloat(),
                        onValueChange = { metres = (it / 100).roundToInt() * 100 },
                        onValueChangeFinished = { save(metres) },
                        valueRange = 100f..5_000f,
                        steps = 48,
                        enabled = !saving,
                        colors = SliderDefaults.colors(
                            thumbColor = SettingsPalette.Accent,
                            activeTrackColor = SettingsPalette.Accent,
                            inactiveTrackColor = SettingsPalette.Border,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    )
                    SettingsActionButton("+", 48.dp, modifier = Modifier.width(48.dp).semantics { contentDescription = increase }, enabled = !saving && metres < 5_000, fontSize = 22.sp) { save(metres + 100) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(String.format(locale, "%.1f km", 0.1), color = SettingsPalette.MutedText, fontSize = 11.sp)
                    Text(String.format(locale, "%.1f km", 5.0), color = SettingsPalette.MutedText, fontSize = 11.sp)
                }
            }
        }
    }
}
