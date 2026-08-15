package com.lito.a5launcher.ui.components

import android.graphics.drawable.Drawable
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.lito.a5launcher.R
import com.lito.a5launcher.AppLanguage
import com.lito.a5launcher.AppLanguageManager
import com.lito.a5launcher.BuildConfig
import com.lito.a5launcher.ConsumptionMetrics
import com.lito.a5launcher.functional.FunctionalEventLogAccess
import com.lito.a5launcher.DeviceRebootAction
import com.lito.a5launcher.LauncherUpdateInstaller
import com.lito.a5launcher.LauncherViewModel
import com.lito.a5launcher.model.AppInfo
import com.lito.a5launcher.model.DoorStatus
import com.lito.a5launcher.assistant.AssistantController
import com.lito.a5launcher.assistant.AssistantErrorLogStats
import com.lito.a5launcher.assistant.AssistantConversationDialog
import com.lito.a5launcher.assistant.AssistantProvider
import com.lito.a5launcher.assistant.AssistantRobotButton
import com.lito.a5launcher.assistant.AssistantSettings
import com.lito.a5launcher.assistant.AssistantSettingsPanel
import com.lito.a5launcher.assistant.AssistantCredentialTester
import com.lito.a5launcher.location.LocationRepository
import com.lito.a5launcher.assistant.AssistantState
import com.lito.a5launcher.assistant.AssistantStatusPanel
import com.lito.a5launcher.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val A5_FUEL_TANK_LITRES = 63f
private const val TOP_COMMAND_ORDER_KEY = "top_command_order"
private const val FOOTER_BLOCK_ORDER_KEY = "footer_block_order"
internal enum class TopCommandItem {
    ASSISTANT,
    NAVIGATION,
    APPS,
    LAUNCHER_SETTINGS,
    DEVICE_SETTINGS,
    RECENTS,
    MMI,
}

internal enum class FooterBlockItem {
    TIME,
    TRIP,
    CONSUMPTION,
    REFUEL_DISTANCE,
    RANGE,
    FUEL,
    WITNESSES,
    ODOMETER,
}

internal val DefaultTopCommandOrder = TopCommandItem.entries.toList()
internal val DefaultFooterBlockOrder = FooterBlockItem.entries.toList()

internal fun parseTopCommandOrder(serialized: String?): List<TopCommandItem> {
    val stored = serialized.orEmpty().split(',').mapNotNull { name ->
        TopCommandItem.entries.firstOrNull { it.name == name }
    }.distinct()
    return stored + DefaultTopCommandOrder.filterNot(stored::contains)
}

internal fun parseFooterBlockOrder(serialized: String?): List<FooterBlockItem> {
    val stored = serialized.orEmpty().split(',').mapNotNull { name ->
        FooterBlockItem.entries.firstOrNull { it.name == name }
    }.distinct()
    return stored + DefaultFooterBlockOrder.filterNot(stored::contains)
}

internal fun moveFooterBlock(
    order: List<FooterBlockItem>,
    item: FooterBlockItem,
    direction: Int,
): List<FooterBlockItem> {
    if (direction == 0) return order
    val from = order.indexOf(item)
    if (from < 0) return order
    val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, order.lastIndex)
    if (from == to) return order
    return order.toMutableList().apply {
        val moved = removeAt(from)
        add(to, moved)
    }
}

/**
 * Distance between the centres of two adjacent footer blocks. A drag swaps only
 * after passing that centre, then subtracts exactly this distance so the drawn
 * block remains aligned with the finger after layouts with unequal widths.
 */
internal fun footerSwapDistancePx(
    order: List<FooterBlockItem>,
    item: FooterBlockItem,
    direction: Int,
    widths: Map<FooterBlockItem, Int>,
    dividerWidthPx: Int = 0,
): Float? {
    val from = order.indexOf(item)
    val targetIndex = from + direction.coerceIn(-1, 1)
    if (from < 0 || targetIndex !in order.indices) return null
    val currentWidth = widths[item] ?: return null
    val targetWidth = widths[order[targetIndex]] ?: return null
    return (currentWidth + targetWidth) / 2f + dividerWidthPx
}

internal fun moveVisibleTopCommand(
    order: List<TopCommandItem>,
    item: TopCommandItem,
    direction: Int,
    visibleItems: Set<TopCommandItem>,
): List<TopCommandItem> {
    if (direction == 0 || item !in visibleItems) return order
    val visible = order.filter(visibleItems::contains)
    val fromVisible = visible.indexOf(item)
    val toVisible = (fromVisible + direction.coerceIn(-1, 1)).coerceIn(0, visible.lastIndex)
    if (fromVisible < 0 || fromVisible == toVisible) return order
    val target = visible[toVisible]
    val from = order.indexOf(item)
    val to = order.indexOf(target)
    return order.toMutableList().apply {
        this[from] = target
        this[to] = item
    }
}

private sealed interface LauncherUpdateState {
    data object Idle : LauncherUpdateState
    data object Preparing : LauncherUpdateState
    data class PermissionRequired(val file: java.io.File) : LauncherUpdateState
    data object Unreadable : LauncherUpdateState
    data object TooLarge : LauncherUpdateState
    data object InvalidApk : LauncherUpdateState
    data object WrongApplication : LauncherUpdateState
    data object InstallerUnavailable : LauncherUpdateState
}

internal fun fuelFraction(litres: Number): Float =
    (litres.toFloat() / A5_FUEL_TANK_LITRES).coerceIn(0f, 1f)

internal fun fuelSegments(litres: Number): Int =
    (fuelFraction(litres) * 10f).roundToInt()

internal enum class FuelSegmentTone { NORMAL, YELLOW, RED }

internal fun fuelSegmentTone(activeSegments: Int): FuelSegmentTone = when (activeSegments) {
    1 -> FuelSegmentTone.RED
    in 2..4 -> FuelSegmentTone.YELLOW
    else -> FuelSegmentTone.NORMAL
}

@Composable
fun DashboardScreen(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val rpm by viewModel.rpm.collectAsStateWithLifecycle()
    val doors by viewModel.doorStatus.collectAsStateWithLifecycle()
    val fuel by viewModel.fuel.collectAsStateWithLifecycle()
    val mileage by viewModel.mileage.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val outside by viewModel.outsideTemp.collectAsStateWithLifecycle()
    val gear by viewModel.gear.collectAsStateWithLifecycle()
    val consumptionMetrics by viewModel.consumptionMetrics.collectAsStateWithLifecycle()
    val tripElapsedRealtimeMs by viewModel.tripElapsedRealtimeMs.collectAsStateWithLifecycle()
    val tripDistanceKm by viewModel.tripDistanceKm.collectAsStateWithLifecycle()
    val distanceSinceRefuelKm by viewModel.distanceSinceRefuelKm.collectAsStateWithLifecycle()
    val seatbelt by viewModel.seatbelt.collectAsStateWithLifecycle()
    val brake by viewModel.parkingBrake.collectAsStateWithLifecycle()
    val lightsOn by viewModel.lightsOn.collectAsStateWithLifecycle()
    val functionalEventLogAccess by viewModel.functionalEventLogAccess.collectAsStateWithLifecycle()
    val dashboardLocale = LocalConfiguration.current.locales[0]
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dashboardLocale) {
        while (true) {
            clockNow = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    val time = remember(clockNow, dashboardLocale) {
        SimpleDateFormat("HH:mm", dashboardLocale).format(Date(clockNow))
    }
    val date = remember(clockNow, dashboardLocale) {
        DateFormat.getDateInstance(DateFormat.FULL, dashboardLocale).format(Date(clockNow))
    }
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val systemNight = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val launcherPreferences = remember {
        context.getSharedPreferences("launcher_settings", android.content.Context.MODE_PRIVATE)
    }
    var showApps by remember { mutableStateOf(false) }
    var showLauncherSettings by remember { mutableStateOf(false) }
    var launcherSettingsTab by remember { mutableStateOf(LauncherSettingsTab.MAP) }
    var topCommandOrder by remember {
        mutableStateOf(parseTopCommandOrder(launcherPreferences.getString(TOP_COMMAND_ORDER_KEY, null)))
    }
    var footerBlockOrder by remember {
        mutableStateOf(parseFooterBlockOrder(launcherPreferences.getString(FOOTER_BLOCK_ORDER_KEY, null)))
    }
    var mapDebugEnabled by remember {
        mutableStateOf(launcherPreferences.getBoolean("map_debug", false))
    }
    var mapTileStyle by remember {
        mutableStateOf(
            MapTileStyle.entries.firstOrNull {
                it.name == launcherPreferences.getString(
                    "map_tile_style",
                    MapTileStyle.POSITRON.name,
                )
            }?.takeUnless { it == MapTileStyle.DARK } ?: MapTileStyle.POSITRON
        )
    }
    var mapColorMode by remember {
        mutableStateOf(
            MapColorMode.entries.firstOrNull {
                it.name == launcherPreferences.getString(
                    "map_color_mode",
                    MapColorMode.AUTOMATIC.name,
                )
            } ?: MapColorMode.AUTOMATIC
        )
    }
    var delayedVehicleLights by remember { mutableStateOf(DelayedVehicleLightsState()) }
    LaunchedEffect(lightsOn) {
        val nowMs = SystemClock.elapsedRealtime()
        delayedVehicleLights = delayedVehicleLights.update(lightsOn, nowMs)
        delayedVehicleLights.remainingDelayMs(nowMs)?.let { remainingMs ->
            kotlinx.coroutines.delay(remainingMs)
            delayedVehicleLights = delayedVehicleLights.update(
                lightsOn = lightsOn,
                nowMs = SystemClock.elapsedRealtime(),
            )
        }
    }
    var mapCacheLimit by remember {
        val storedCacheLimit = launcherPreferences.getString(
            "map_cache_limit",
            MapCacheLimit.GB_2.name,
        )
        mutableStateOf(
            MapCacheLimit.entries.firstOrNull { it.name == storedCacheLimit }
                ?: MapCacheLimit.GB_2
        )
    }
    val darkModeActive = resolveMapTileStyle(
        colorMode = mapColorMode,
        vehicleLightsOn = delayedVehicleLights.effectiveLightsOn,
        systemNight = systemNight,
        preferredLightStyle = mapTileStyle,
    ) == MapTileStyle.DARK
    val startupProgress = rememberOemStartupProgress()
    var cacheGeneration by remember { mutableIntStateOf(0) }
    var maintenanceSizeGeneration by remember { mutableIntStateOf(0) }
    var maintenanceMessage by remember { mutableStateOf("") }
    var mapCacheSizeBytes by remember { mutableLongStateOf(0L) }
    var debugLogStats by remember { mutableStateOf(MapDebugLogStats(0, 0L)) }
    var mapDiagnostics by remember { mutableStateOf(MapDiagnostics()) }
    val coroutineScope = rememberCoroutineScope()
    val poiRepository = remember { PoiRepository(context.applicationContext) }
    val locationRepository = remember(context) { LocationRepository(context) }
    var poiSnapshot by remember { mutableStateOf(PoiSnapshot()) }
    var poiNotice by remember { mutableStateOf<FloatingNotification?>(null) }
    LaunchedEffect(poiNotice) {
        if (poiNotice != null) {
            kotlinx.coroutines.delay(FLOATING_NOTIFICATION_VISIBLE_MS)
            poiNotice = null
        }
    }
    fun applyPoiImportResult(result: Result<PoiSnapshot>) {
        result.onSuccess {
            poiSnapshot = it
            poiNotice = FloatingNotification(
                resources.getString(R.string.poi_import_success),
                FloatingNotificationTone.SUCCESS,
            )
        }.onFailure {
            poiNotice = FloatingNotification(
                resources.getString(R.string.poi_import_failed),
                FloatingNotificationTone.ERROR,
            )
        }
    }
    fun selectedFileName(uri: android.net.Uri): String = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    val poiGeoJsonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        poiRepository.importGeoJson(selectedFileName(uri), it)
                    } ?: error(resources.getString(R.string.poi_read_error))
                }
            }
            applyPoiImportResult(result)
        }
    }
    val poiIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        poiRepository.importIcon(selectedFileName(uri), it)
                    } ?: error(resources.getString(R.string.poi_read_error))
                }
            }
            applyPoiImportResult(result)
        }
    }
    val poiCategoriesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        poiRepository.importCategories(selectedFileName(uri), it)
                    } ?: error(resources.getString(R.string.poi_read_error))
                }
            }
            applyPoiImportResult(result)
        }
    }
    val assistantController = remember(context, viewModel, coroutineScope) {
        AssistantController(
            context,
            coroutineScope,
            viewModel::launchNavigation,
            locationRepository,
        )
    }
    LaunchedEffect(poiRepository) {
        poiSnapshot = poiRepository.snapshot()
    }
    val assistantUi by assistantController.uiState.collectAsStateWithLifecycle()
    DisposableEffect(assistantController) {
        onDispose { assistantController.release() }
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) assistantController.startTurn()
    }
    val startAssistantTurn = {
        if (
            assistantController.settings.provider == AssistantProvider.DISABLED ||
            !assistantController.settings.hasApiKey(assistantController.settings.provider)
        ) {
            launcherSettingsTab = LauncherSettingsTab.ASSISTANT
            showLauncherSettings = true
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            assistantController.startTurn()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var updateState by remember { mutableStateOf<LauncherUpdateState>(LauncherUpdateState.Idle) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val permissionState = updateState as? LauncherUpdateState.PermissionRequired
        if (permissionState != null && LauncherUpdateInstaller.canInstallPackages(context)) {
            updateState = if (
                LauncherUpdateInstaller.install(context, permissionState.file).isFailure
            ) {
                LauncherUpdateState.InstallerUnavailable
            } else {
                LauncherUpdateState.Idle
            }
        }
    }
    val updateApkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { selectedApk ->
        if (selectedApk != null) {
            updateState = LauncherUpdateState.Preparing
            coroutineScope.launch {
                val preparation = withContext(Dispatchers.IO) {
                    LauncherUpdateInstaller.prepare(context, selectedApk)
                }
                when (preparation) {
                    is LauncherUpdateInstaller.PreparationResult.Ready -> {
                        if (LauncherUpdateInstaller.canInstallPackages(context)) {
                            updateState = if (
                                LauncherUpdateInstaller.install(context, preparation.file).isFailure
                            ) {
                                LauncherUpdateState.InstallerUnavailable
                            } else {
                                LauncherUpdateState.Idle
                            }
                        } else {
                            updateState = LauncherUpdateState.PermissionRequired(preparation.file)
                            installPermissionLauncher.launch(
                                LauncherUpdateInstaller.requestPermissionIntent(context),
                            )
                        }
                    }
                    LauncherUpdateInstaller.PreparationResult.Unreadable -> {
                        updateState = LauncherUpdateState.Unreadable
                    }
                    LauncherUpdateInstaller.PreparationResult.TooLarge -> {
                        updateState = LauncherUpdateState.TooLarge
                    }
                    LauncherUpdateInstaller.PreparationResult.InvalidApk -> {
                        updateState = LauncherUpdateState.InvalidApk
                    }
                    LauncherUpdateInstaller.PreparationResult.WrongApplication -> {
                        updateState = LauncherUpdateState.WrongApplication
                    }
                }
            }
        }
    }
    var pendingErrorLogExport by remember {
        mutableStateOf<((Boolean?) -> Unit)?>(null)
    }
    val errorLogDestinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        val onResult = pendingErrorLogExport
        pendingErrorLogExport = null
        if (destination == null) {
            onResult?.invoke(null)
        } else {
            assistantController.exportErrorLogs(destination) { exported ->
                onResult?.invoke(exported)
            }
        }
    }
    var pendingMapLogExport by remember {
        mutableStateOf<((Boolean?) -> Unit)?>(null)
    }
    val mapLogDestinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        val onResult = pendingMapLogExport
        pendingMapLogExport = null
        if (destination == null) {
            onResult?.invoke(null)
        } else {
            coroutineScope.launch {
                val exported = MapDebugLogger.get(context).export(destination)
                onResult?.invoke(exported)
            }
        }
    }

    LaunchedEffect(
        showLauncherSettings,
        maintenanceSizeGeneration,
        mapDiagnostics.debugLogPath,
    ) {
        if (showLauncherSettings) {
            val sizes = withContext(Dispatchers.IO) {
                val mapCacheSize = async { vectorMapCacheSizeBytes(context) }
                val debugLogs = async { MapDebugLogger.stats(context) }
                mapCacheSize.await() to debugLogs.await()
            }
            mapCacheSizeBytes = sizes.first
            debugLogStats = sizes.second
            poiSnapshot = poiRepository.snapshot()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = maxWidth
        val commandBarHeight = maxHeight * .125f
        val dialSize = (maxHeight - commandBarHeight * 2f).coerceAtMost(maxWidth * .305f)
        val commandButtonSize = maxHeight * .095f
        val commandIconSize = maxHeight * .067f
        val headerTextSize = (maxHeight.value * .040f).sp
        val vitalsHeight = commandBarHeight
        val vitalsLabelSize = (maxHeight.value * .024f).sp
        val vitalsValueSize = (maxHeight.value * .038f).sp
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CockpitBackdrop(
                Modifier.graphicsLayer {
                    alpha = oemStartupPresentation(startupProgress.value).contentAlpha
                },
            )

            Column(Modifier.fillMaxSize()) {
                TopCommandBar(
                    date = date,
                    time = time,
                    outside = outside,
                    onNavigation = viewModel::launchWaze,
                    onAssistant = startAssistantTurn,
                    assistantEnabled = assistantController.settings.provider != AssistantProvider.DISABLED,
                    assistantActive = assistantUi.status !is AssistantState.Disabled &&
                        assistantUi.status !is AssistantState.Ready,
                    onApps = { showApps = true },
                    onRecents = viewModel::openRecentApps,
                    onMmi = viewModel::launchOriginalMMI,
                    onSettings = viewModel::launchSettings,
                    onLauncherSettings = {
                        launcherSettingsTab = LauncherSettingsTab.MAP
                        showLauncherSettings = true
                    },
                    commandOrder = topCommandOrder,
                    onCommandOrderChanged = { order ->
                        if (order !== topCommandOrder) {
                            topCommandOrder = order
                            launcherPreferences.edit {
                                putString(TOP_COMMAND_ORDER_KEY, order.joinToString(",") { it.name })
                            }
                        }
                    },
                    barHeight = commandBarHeight,
                    buttonSize = commandButtonSize,
                    iconSize = commandIconSize,
                    textSize = headerTextSize,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = oemStartupPresentation(startupProgress.value).contentAlpha
                        }
                        .zIndex(2f),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .zIndex(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CockpitMap(
                        modifier = Modifier.fillMaxSize(),
                        tileStyle = mapTileStyle,
                        colorMode = mapColorMode,
                        vehicleLightsOn = delayedVehicleLights.effectiveLightsOn,
                        cacheLimit = mapCacheLimit,
                        cacheGeneration = cacheGeneration,
                        poiSnapshot = poiSnapshot,
                        poiRepository = poiRepository,
                        locationRepository = locationRepository,
                        debugEnabled = mapDebugEnabled,
                        onDiagnosticsChanged = { mapDiagnostics = it },
                    )

                    Canvas(Modifier.fillMaxSize()) {
                        val hiddenAlpha = 1f -
                            oemStartupPresentation(startupProgress.value).contentAlpha
                        if (hiddenAlpha > 0f) {
                            drawRect(Color.Black.copy(alpha = hiddenAlpha))
                        }
                    }

                    AssistantStatusPanel(
                        state = assistantUi.status,
                        audioLevel = assistantUi.audioLevel,
                        action = assistantUi.action,
                        heardText = assistantUi.heardText,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                            .graphicsLayer {
                                alpha = oemStartupPresentation(startupProgress.value).contentAlpha
                            }
                            .zIndex(3f),
                    )

                    CockpitMapIntegrationOverlay(
                        dialDiameter = dialSize,
                        modifier = Modifier.fillMaxSize(),
                    )

                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(
                                    x = screenWidth * OEM_LEFT_DIAL_CENTER_FRACTION - dialSize / 2f,
                                )
                                .size(dialSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProgressRingIndicator(
                                modifier = Modifier.size(dialSize),
                                value = speed,
                                maxValue = 280,
                                dialType = DialType.SPEED,
                                label = "",
                                startupProgress = startupProgress,
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(
                                    x = screenWidth * OEM_RIGHT_DIAL_CENTER_FRACTION - dialSize / 2f,
                                )
                                .size(dialSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProgressRingIndicator(
                                modifier = Modifier.size(dialSize),
                                value = rpm,
                                maxValue = 6000,
                                dialType = DialType.RPM,
                                label = "",
                                centerGear = gear,
                                startupProgress = startupProgress,
                            )
                        }
                    }
                }

                CompactVitals(
                    fuel = fuel,
                    mileage = mileage,
                    range = range,
                    consumptionMetrics = consumptionMetrics,
                    tripElapsedRealtimeMs = tripElapsedRealtimeMs,
                    tripDistanceKm = tripDistanceKm,
                    distanceSinceRefuelKm = distanceSinceRefuelKm,
                    barHeight = vitalsHeight,
                    labelSize = vitalsLabelSize,
                    valueSize = vitalsValueSize,
                    doors = doors,
                    seatbeltAlert = !seatbelt,
                    parkingBrake = brake,
                    lightsActive = darkModeActive,
                    blockOrder = footerBlockOrder,
                    onBlockOrderChanged = { order ->
                        if (order !== footerBlockOrder) {
                            footerBlockOrder = order
                            launcherPreferences.edit {
                                putString(FOOTER_BLOCK_ORDER_KEY, order.joinToString(",") { it.name })
                            }
                        }
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = oemStartupPresentation(startupProgress.value).contentAlpha
                        }
                        .zIndex(2f),
                )
            }

            AnimatedVisibility(
                visible = showApps,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                AppsOverlay(
                    apps = apps,
                    headerHeight = commandBarHeight,
                    onClose = { showApps = false },
                    onLaunch = {
                        viewModel.launchApp(it)
                        showApps = false
                    },
                    onAppInfo = viewModel::openAppInfo,
                )
            }

            AnimatedVisibility(
                visible = showLauncherSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                LauncherSettingsOverlay(
                    headerHeight = commandBarHeight,
                    selectedTab = launcherSettingsTab,
                    onSelectedTabChanged = { launcherSettingsTab = it },
                    diagnostics = mapDiagnostics,
                    mapTileStyle = mapTileStyle,
                    onMapTileStyleChanged = { style ->
                        mapTileStyle = style
                        launcherPreferences.edit {
                            putString("map_tile_style", style.name)
                        }
                    },
                    mapColorMode = mapColorMode,
                    onMapColorModeChanged = { mode ->
                        mapColorMode = mode
                        launcherPreferences.edit {
                            putString("map_color_mode", mode.name)
                        }
                    },
                    mapCacheLimit = mapCacheLimit,
                    onMapCacheLimitChanged = { limit ->
                        mapCacheLimit = limit
                        launcherPreferences.edit {
                            putString("map_cache_limit", limit.name)
                        }
                    },
                    mapDebugEnabled = mapDebugEnabled,
                    onMapDebugChanged = { enabled ->
                        mapDebugEnabled = enabled
                        launcherPreferences.edit { putBoolean("map_debug", enabled) }
                    },
                    maintenanceMessage = maintenanceMessage,
                    mapCacheSizeBytes = mapCacheSizeBytes,
                    debugLogStats = debugLogStats,
                    onClearMapCache = {
                        coroutineScope.launch {
                            val deleted = clearVectorMapCache(context)
                            cacheGeneration++
                            maintenanceSizeGeneration++
                            maintenanceMessage = if (deleted > 0) {
                                resources.getString(R.string.map_cache_cleared_legacy, deleted)
                            } else {
                                resources.getString(R.string.map_cache_cleared)
                            }
                        }
                    },
                    onClearDebugLogs = {
                        mapDebugEnabled = false
                        launcherPreferences.edit { putBoolean("map_debug", false) }
                        coroutineScope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                MapDebugLogger.deleteAll(context)
                            }
                            maintenanceMessage = resources.getString(R.string.map_logs_cleared, deleted)
                            debugLogStats = MapDebugLogStats(0, 0L)
                            maintenanceSizeGeneration++
                            mapDiagnostics = mapDiagnostics.copy(debugLogPath = "")
                        }
                    },
                    onExportDebugLogs = { onResult ->
                        if (debugLogStats.fileCount == 0) {
                            maintenanceMessage = resources.getString(R.string.map_no_logs)
                            onResult(false)
                        } else {
                            pendingMapLogExport = { exported ->
                                maintenanceMessage = when (exported) {
                                    true -> resources.getString(R.string.map_logs_exported)
                                    false -> resources.getString(R.string.map_logs_export_failed)
                                    null -> ""
                                }
                                onResult(exported)
                            }
                            mapLogDestinationPicker.launch(
                                MapDebugLogger.get(context).suggestedExportName()
                            )
                        }
                    },
                    poiSnapshot = poiSnapshot,
                    poiNotice = poiNotice,
                    onImportPoiSource = {
                        poiNotice = null
                        poiGeoJsonPicker.launch(
                            arrayOf(
                                "application/geo+json",
                                "application/json",
                                "application/octet-stream",
                            )
                        )
                    },
                    onImportPoiIcon = {
                        poiNotice = null
                        poiIconPicker.launch(arrayOf("image/png"))
                    },
                    onImportPoiCategories = {
                        poiNotice = null
                        poiCategoriesPicker.launch(arrayOf("application/json"))
                    },
                    onDeletePoiSource = { name ->
                        coroutineScope.launch {
                            poiSnapshot = poiRepository.deleteSource(name)
                        }
                    },
                    onDeletePoiIcon = { code ->
                        coroutineScope.launch {
                            poiSnapshot = poiRepository.deleteIcon(code)
                        }
                    },
                    onDeletePoiCategories = {
                        coroutineScope.launch {
                            poiSnapshot = poiRepository.deleteCategories()
                        }
                    },
                    assistantSettings = assistantController.settings,
                    assistantCredentialTester = assistantController,
                    readAssistantErrorLogStats = assistantController::errorLogStats,
                    onExportAssistantErrorLogs = { onResult ->
                        if (assistantController.errorLogStats().fileCount == 0) {
                            onResult(false)
                        } else {
                            pendingErrorLogExport = onResult
                            errorLogDestinationPicker.launch(assistantController.errorLogExportName())
                        }
                    },
                    onClearAssistantErrorLogs = assistantController::clearErrorLogs,
                    onAssistantSaved = assistantController::refreshSettings,
                    functionalEventLogAccess = functionalEventLogAccess,
                    updateState = updateState,
                    onSelectUpdateApk = {
                        updateApkPicker.launch(arrayOf(LauncherUpdateInstaller.APK_MIME_TYPE))
                    },
                    onRequestDeviceReboot = { DeviceRebootAction.request(context) },
                    onClose = { showLauncherSettings = false },
                )
            }

            assistantUi.response?.let { response ->
                AssistantConversationDialog(
                    response = response,
                    onRespond = startAssistantTurn,
                    onRepeat = assistantController::repeatResponse,
                    onClose = assistantController::closeConversation,
                )
            }
        }
    }
}

@Composable
private fun TopCommandBar(
    date: String,
    time: String,
    outside: Double,
    onNavigation: () -> Unit,
    onAssistant: () -> Unit,
    assistantEnabled: Boolean,
    assistantActive: Boolean,
    onApps: () -> Unit,
    onRecents: () -> Unit,
    onMmi: () -> Unit,
    onSettings: () -> Unit,
    onLauncherSettings: () -> Unit,
    commandOrder: List<TopCommandItem>,
    onCommandOrderChanged: (List<TopCommandItem>) -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val visibleCommands = remember(commandOrder, assistantEnabled) {
        commandOrder.filter { assistantEnabled || it != TopCommandItem.ASSISTANT }
    }
    val commandDragThreshold = buttonSize * .55f
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Color.Black),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = time,
                    color = Color.White,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = "·",
                    color = Color.White,
                    fontSize = textSize * .48f,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = date,
                    color = Color.White,
                    fontSize = textSize * .62f,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(unbounded = true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                visibleCommands.forEachIndexed { index, item ->
                    key(item) {
                        ReorderableCommandSlot(
                            item = item,
                            dragThreshold = commandDragThreshold,
                            onMove = { direction ->
                                onCommandOrderChanged(
                                    moveVisibleTopCommand(
                                        order = commandOrder,
                                        item = item,
                                        direction = direction,
                                        visibleItems = visibleCommands.toSet(),
                                    ),
                                )
                            },
                        ) {
                            when (item) {
                                TopCommandItem.ASSISTANT -> AssistantRobotButton(
                                    buttonSize = buttonSize,
                                    iconSize = iconSize,
                                    active = assistantActive,
                                    onClick = onAssistant,
                                )
                                TopCommandItem.NAVIGATION -> NavigationCommandButton(
                                    buttonSize,
                                    iconSize,
                                    onNavigation,
                                )
                                TopCommandItem.APPS -> AppsCommandButton(
                                    buttonSize,
                                    iconSize,
                                    onApps,
                                )
                                TopCommandItem.LAUNCHER_SETTINGS -> LauncherSettingsCommandButton(
                                    buttonSize,
                                    iconSize,
                                    onLauncherSettings,
                                )
                                TopCommandItem.DEVICE_SETTINGS -> CommandButton(
                                    Icons.Default.Settings,
                                    stringResource(R.string.device_settings_description),
                                    buttonSize,
                                    iconSize,
                                    .88f,
                                    onSettings,
                                )
                                TopCommandItem.RECENTS -> RecentAppsCommandButton(
                                    buttonSize,
                                    iconSize,
                                    onRecents,
                                )
                                TopCommandItem.MMI -> CarCommandButton(
                                    buttonSize,
                                    iconSize * 1.05f,
                                    onMmi,
                                )
                            }
                        }
                    }
                    if (index != visibleCommands.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(iconSize * .68f),
                        ) {
                            Box(Modifier.fillMaxSize().background(OemCockpitTokens.Titanium.copy(alpha = .18f)))
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                if (outside.isFinite()) String.format(locale, "%.1f°C", outside) else "—",
                color = Color.White,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
            )
        }
        }
    }
}

@Composable
private fun ReorderableCommandSlot(
    item: TopCommandItem,
    dragThreshold: androidx.compose.ui.unit.Dp,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    Box(
        Modifier
            .graphicsLayer {
                translationX = dragDistance
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
            }
            .zIndex(if (dragging) 4f else 0f)
            .pointerInput(item, dragThreshold) {
                val threshold = dragThreshold.toPx()
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragDistance = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.x
                        when {
                            dragDistance > threshold -> {
                                currentOnMove(1)
                                dragDistance -= threshold * 1.8f
                            }
                            dragDistance < -threshold -> {
                                currentOnMove(-1)
                                dragDistance += threshold * 1.8f
                            }
                        }
                    },
                )
            }
            .then(
                if (dragging) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OemCockpitTokens.GraphitePressed)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LauncherSettingsCommandButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    CommandSurface(buttonSize, onClick) {
        Canvas(Modifier.size(iconSize * .72f)) {
            val strokeWidth = 1.2.dp.toPx()
            val knobRadius = 2.8.dp.toPx()
            val lineInset = strokeWidth / 2f
            val positions = listOf(
                .30f to .15f,
                .68f to .50f,
                .42f to .85f,
            )
            positions.forEach { (knobX, lineY) ->
                drawLine(
                    color = Color.White,
                    start = Offset(lineInset, size.height * lineY),
                    end = Offset(size.width - lineInset, size.height * lineY),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                drawCircle(
                    color = Color.Black,
                    radius = knobRadius + strokeWidth,
                    center = Offset(size.width * knobX, size.height * lineY),
                )
                drawCircle(
                    color = Color.White,
                    radius = knobRadius,
                    center = Offset(size.width * knobX, size.height * lineY),
                )
            }
        }
    }
}

@Composable
private fun CommandButton(
    icon: ImageVector,
    label: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    opticalScale: Float,
    onClick: () -> Unit,
) {
    CommandSurface(buttonSize, onClick) {
        Icon(
            icon,
            label,
            tint = Color.White,
            modifier = Modifier.size(iconSize * opticalScale),
        )
    }
}

@Composable
private fun NavigationCommandButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    CommandSurface(buttonSize, onClick) {
        Canvas(
            Modifier.size(
                width = iconSize * .56f,
                height = iconSize * .84f,
            ),
        ) {
            val width = size.width
            val height = size.height
            val capHeight = width
            val center = Offset(width / 2f, capHeight / 2f)
            val pin = Path().apply {
                moveTo(width / 2f, 0f)
                cubicTo(width * .79f, 0f, width, capHeight * .25f, width, capHeight * .52f)
                cubicTo(width, height * .66f, width * .67f, height * .85f, width / 2f, height)
                cubicTo(width * .33f, height * .85f, 0f, height * .66f, 0f, capHeight * .52f)
                cubicTo(0f, capHeight * .25f, width * .21f, 0f, width / 2f, 0f)
                close()
            }
            drawPath(pin, Color.White)
            drawCircle(Color.Black, radius = width * .16f, center = center)
        }
    }
}

@Composable
private fun CarCommandButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    CommandSurface(buttonSize, onClick) {
        Canvas(
            Modifier.size(
                width = iconSize * 1.28f,
                height = iconSize * .48f,
            )
        ) {
            val ringsColor = Color.White
            val strokeWidth = size.height * .09f
            val ringDiameter = size.width * .26f
            val ringRadius = ringDiameter / 2f
            val ringSpacing = size.width * .21f
            val leftInset = size.width * .055f
            val centerY = size.height / 2f

            repeat(4) { index ->
                drawCircle(
                    color = ringsColor,
                    radius = ringRadius,
                    center = Offset(
                        x = leftInset + ringRadius + index * ringSpacing,
                        y = centerY,
                    ),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

@Composable
private fun AppsCommandButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    CommandSurface(buttonSize, onClick) {
        Canvas(Modifier.size(iconSize * .72f)) {
            val stroke = Stroke(
                width = 1.8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
            val inset = stroke.width / 2f
            val available = size.minDimension - stroke.width
            val gap = available * .18f
            val cell = (available - gap) / 2f
            listOf(
                Offset(inset, inset),
                Offset(inset + cell + gap, inset),
                Offset(inset, inset + cell + gap),
                Offset(inset + cell + gap, inset + cell + gap),
            ).forEach { origin ->
                drawRoundRect(
                    Color.White,
                    topLeft = origin,
                    size = Size(cell, cell),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * .12f),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun RecentAppsCommandButton(
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    CommandSurface(buttonSize, onClick) {
        Canvas(Modifier.size(iconSize * .74f)) {
            val stroke = Stroke(width = 1.8.dp.toPx())
            val corner = 2.dp.toPx()
            val inset = stroke.width / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = .55f),
                topLeft = Offset(size.width * .22f, inset),
                size = Size(size.width * .78f - inset, size.height * .72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                style = stroke,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(inset, size.height * .28f),
                size = Size(size.width * .78f, size.height * .72f - inset),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                style = stroke,
            )
        }
    }
}

@Composable
private fun CockpitBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val cyan = Color(0xFF4CEEFF)
        for (i in 0..24) {
            val x = size.width * i / 24f
            drawLine(
                cyan.copy(alpha = .022f),
                Offset(size.width / 2, size.height * .40f),
                Offset(x, size.height),
                1f,
            )
        }
        drawLine(
            cyan.copy(alpha = .12f),
            Offset(size.width * .08f, size.height * .40f),
            Offset(size.width * .92f, size.height * .40f),
            1.dp.toPx(),
        )
    }
}

@Composable
private fun BottomStatusPanel(
    doors: DoorStatus,
    seatbeltAlert: Boolean,
    parkingBrake: Boolean,
    lightsActive: Boolean,
) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DoorStatusWitness(doors)
        StatusWitness(seatbeltAlert, WitnessType.Seatbelt)
        StatusWitness(parkingBrake, WitnessType.Parking)
        StatusWitness(lightsActive, WitnessType.Lights)
    }
}

private enum class WitnessType { Seatbelt, Parking, Lights }
private enum class DoorRegion { FrontLeft, FrontRight, RearLeft, RearRight }

@Composable
private fun DoorStatusWitness(doors: DoorStatus) {
    val alertColor = Color(0xFFFF3B3B)
    val inactiveColor = Color(0xFF263036)
    val anyOpen = doors.driverOpen || doors.passengerOpen ||
        doors.rearLeftOpen || doors.rearRightOpen || doors.hoodOpen || doors.trunkOpen

    val pulse = rememberWitnessPulse(anyOpen)
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        WitnessPulseRing(pulse, alertColor)
        // Exact complete SVG supplied for this witness. It is the inactive
        // base, so no independently scaled door can alter its silhouette.
        Icon(
            painter = painterResource(R.drawable.ic_witness_door),
            contentDescription = null,
            tint = inactiveColor,
            modifier = Modifier.size(30.dp),
        )
        // The body has its own wider viewport; the four door layers preserve
        // their original scale and separation.
        Icon(
            painter = painterResource(R.drawable.ic_witness_door_body),
            contentDescription = null,
            tint = if (anyOpen) alertColor else inactiveColor,
            modifier = Modifier.size(30.dp),
        )
        // The top-down SVG is mirrored relative to the CAN left/right naming.
        if (doors.driverOpen) DoorLayer(DoorRegion.FrontRight, alertColor)
        if (doors.passengerOpen) DoorLayer(DoorRegion.FrontLeft, alertColor)
        if (doors.rearLeftOpen) DoorLayer(DoorRegion.RearRight, alertColor)
        if (doors.rearRightOpen) DoorLayer(DoorRegion.RearLeft, alertColor)
    }
}

@Composable
private fun DoorLayer(region: DoorRegion, color: Color) {
    Icon(
        painter = painterResource(R.drawable.ic_witness_door),
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(30.dp)
            .drawWithContent doorLayer@{
                // Coordinates come directly from the 717 × 717 VectorDrawable.
                // Each active door is therefore a clipped portion of the exact
                // same path used by the inactive base—never a scaled copy.
                val bodyLeft = size.width * (171f / 717f)
                val bodyRight = size.width * (546f / 717f)
                val splitY = size.height * 0.55f
                when (region) {
                    DoorRegion.FrontLeft -> clipRect(0f, 0f, bodyLeft, splitY) { this@doorLayer.drawContent() }
                    DoorRegion.FrontRight -> clipRect(bodyRight, 0f, size.width, splitY) { this@doorLayer.drawContent() }
                    DoorRegion.RearLeft -> clipRect(0f, splitY, bodyLeft, size.height) { this@doorLayer.drawContent() }
                    DoorRegion.RearRight -> clipRect(bodyRight, splitY, size.width, size.height) { this@doorLayer.drawContent() }
                }
            },
    )
}

@Composable
private fun StatusWitness(active: Boolean, type: WitnessType) {
    val color = when {
        !active -> Color(0xFF263036)
        type == WitnessType.Lights -> Color(0xFF42D36B)
        else -> Color(0xFFFF3B3B)
    }
    val icon = when (type) {
        WitnessType.Seatbelt -> R.drawable.ic_witness_seatbelt
        WitnessType.Parking -> R.drawable.ic_witness_parking_brake
        WitnessType.Lights -> R.drawable.ic_witness_lights
    }
    val iconSize = if (type == WitnessType.Seatbelt) 34.dp else 38.dp
    val pulse = rememberWitnessPulse(active)
    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
        WitnessPulseRing(pulse, color)
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun rememberWitnessPulse(active: Boolean): Animatable<Float, AnimationVector1D> {
    val pulse = remember { Animatable(0f) }
    var previousActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        val shouldPulse = active && !previousActive
        previousActive = active
        if (shouldPulse) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(220))
        } else {
            pulse.snapTo(0f)
        }
    }
    return pulse
}

@Composable
private fun BoxScope.WitnessPulseRing(
    progress: Animatable<Float, AnimationVector1D>,
    color: Color,
) {
    Canvas(Modifier.matchParentSize().graphicsLayer {
        val value = progress.value
        scaleX = 1f + (1f - value) * .18f
        scaleY = 1f + (1f - value) * .18f
        alpha = value
    }) {
        drawCircle(
            color = color.copy(alpha = .42f),
            radius = size.minDimension * .49f,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun CompactVitals(
    fuel: Int,
    mileage: Int,
    range: Int,
    consumptionMetrics: ConsumptionMetrics,
    tripElapsedRealtimeMs: Long,
    tripDistanceKm: Double,
    distanceSinceRefuelKm: Double,
    barHeight: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    doors: DoorStatus,
    seatbeltAlert: Boolean,
    parkingBrake: Boolean,
    lightsActive: Boolean,
    blockOrder: List<FooterBlockItem>,
    onBlockOrderChanged: (List<FooterBlockItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showConsumptionDetails by remember { mutableStateOf(false) }
    val footerWidths = remember { mutableStateMapOf<FooterBlockItem, Int>() }
    val footerDividerWidthPx = with(LocalDensity.current) { 1.dp.roundToPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Color.Black),
    ) {
        Row(
            Modifier.fillMaxSize().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            blockOrder.forEachIndexed { index, item ->
                key(item) {
                    FooterReorderableSlot(
                        item = item,
                        order = blockOrder,
                        widths = footerWidths,
                        dividerWidthPx = footerDividerWidthPx,
                        onMove = onBlockOrderChanged,
                        modifier = if (item == FooterBlockItem.WITNESSES) Modifier.weight(1f) else Modifier,
                    ) {
                        when (item) {
                        FooterBlockItem.TIME -> FooterBlock {
                            MiniValue(stringResource(R.string.dashboard_time), formatTripDuration(tripElapsedRealtimeMs), false, labelSize, valueSize)
                        }
                        FooterBlockItem.TRIP -> FooterBlock {
                            MiniValue(stringResource(R.string.dashboard_trip), formatTripDistance(tripDistanceKm, locale), false, labelSize, valueSize)
                        }
                        FooterBlockItem.CONSUMPTION -> FooterBlock(
                            modifier = Modifier.clickable { showConsumptionDetails = true },
                        ) {
                            MiniValue(
                                stringResource(R.string.dashboard_consumption),
                                if (consumptionMetrics.calculated.isFinite()) {
                                    String.format(locale, "%.1f", consumptionMetrics.calculated)
                                } else "—",
                                false,
                                labelSize,
                                valueSize,
                            )
                        }
                        FooterBlockItem.REFUEL_DISTANCE -> FooterBlock {
                            MiniValue(stringResource(R.string.dashboard_refuel_distance), formatTripDistance(distanceSinceRefuelKm, locale), false, labelSize, valueSize)
                        }
                        FooterBlockItem.RANGE -> FooterBlock {
                            MiniValue(
                                stringResource(R.string.dashboard_range),
                                if (range > 0) formatDashboardInteger(range, locale) else "—",
                                range in 1..79,
                                labelSize,
                                valueSize,
                            )
                        }
                        FooterBlockItem.FUEL -> FooterBlock { FuelValue(fuel, valueSize) }
                        FooterBlockItem.WITNESSES -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomStatusPanel(doors, seatbeltAlert, parkingBrake, lightsActive)
                        }
                        FooterBlockItem.ODOMETER -> FooterBlock {
                            MiniValue(
                                stringResource(R.string.dashboard_odometer),
                                if (mileage > 0) formatDashboardInteger(mileage, locale) else "—",
                                false,
                                labelSize,
                                valueSize,
                            )
                        }
                        }
                    }
                }
                if (index != blockOrder.lastIndex) FooterDivider()
            }
        }

        if (showConsumptionDetails) {
            ConsumptionDetailsDialog(
                calculatedConsumption = consumptionMetrics.calculated,
                observedCanConsumption = consumptionMetrics.observedCan,
                locale = locale,
                onDismiss = { showConsumptionDetails = false },
            )
        }
    }
}

@Composable
private fun FooterReorderableSlot(
    item: FooterBlockItem,
    order: List<FooterBlockItem>,
    widths: MutableMap<FooterBlockItem, Int>,
    dividerWidthPx: Int,
    onMove: (List<FooterBlockItem>) -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOrder by rememberUpdatedState(order)
    val currentWidths by rememberUpdatedState(widths)
    var dragOrder by remember { mutableStateOf(order) }
    Box(
        modifier
            .fillMaxHeight()
            .onSizeChanged { widths[item] = it.width }
            .graphicsLayer {
                translationX = dragDistance
                scaleX = if (dragging) 1.035f else 1f
                scaleY = if (dragging) 1.035f else 1f
            }
            .zIndex(if (dragging) 4f else 0f)
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragDistance = 0f
                        dragOrder = currentOrder
                    },
                    onDragCancel = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.x
                        val rightDistance = footerSwapDistancePx(
                            // Keep the active drag order local so an immediate
                            // second pointer event sees the block's new neighbour.
                            dragOrder,
                            item,
                            1,
                            currentWidths,
                            dividerWidthPx,
                        )
                        val leftDistance = footerSwapDistancePx(
                            dragOrder,
                            item,
                            -1,
                            currentWidths,
                            dividerWidthPx,
                        )
                        when {
                            rightDistance != null && dragDistance > rightDistance -> {
                                dragOrder = moveFooterBlock(dragOrder, item, 1)
                                currentOnMove(dragOrder)
                                dragDistance -= rightDistance
                            }
                            leftDistance != null && dragDistance < -leftDistance -> {
                                dragOrder = moveFooterBlock(dragOrder, item, -1)
                                currentOnMove(dragOrder)
                                dragDistance += leftDistance
                            }
                        }
                    },
                )
            }
            .then(
                if (dragging) Modifier.background(OemCockpitTokens.GraphitePressed) else Modifier,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

internal fun formatTripDuration(elapsedRealtimeMs: Long): String {
    val totalMinutes = elapsedRealtimeMs.coerceAtLeast(0L) / 60_000L
    return String.format(
        java.util.Locale.US,
        "%02d:%02d",
        totalMinutes / 60L,
        totalMinutes % 60L,
    )
}

internal fun formatTripDistance(
    distanceKm: Double,
    locale: java.util.Locale = java.util.Locale.getDefault(),
): String = String.format(locale, "%.1f", distanceKm)

@Composable
private fun FooterCell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FooterBlock(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    FooterCell(modifier.padding(horizontal = horizontalPadding), content)
}

@Composable
private fun FooterDivider() {
    Box(
        Modifier
            .fillMaxHeight(.68f)
            .width(1.dp)
            .background(Color.White.copy(alpha = .12f))
    )
}

@Composable
private fun FuelValue(
    fuel: Int,
    valueSize: androidx.compose.ui.unit.TextUnit,
) {
    val activeSegments = fuelSegments(fuel)
    val segmentTone = fuelSegmentTone(activeSegments)
    val contentColor = when (segmentTone) {
        FuelSegmentTone.RED -> SLineRed
        FuelSegmentTone.YELLOW -> Color(0xFFFFC400)
        FuelSegmentTone.NORMAL -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FuelPumpIcon(
                color = contentColor.copy(alpha = if (segmentTone == FuelSegmentTone.NORMAL) .62f else 1f),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(10) { index ->
                    val segmentActive = index < activeSegments
                    val segmentColor = when {
                        !segmentActive -> Color(0xFF273037)
                        segmentTone == FuelSegmentTone.RED -> SLineRed
                        segmentTone == FuelSegmentTone.YELLOW -> Color(0xFFFFC400)
                        else -> Color(0xFF00DDF4)
                    }
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(segmentColor)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                fuel.toString(),
                color = contentColor,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FuelPumpIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.drawWithCache {
        val stroke = Stroke(width = size.width * .085f)
        val hose = Path().apply {
            moveTo(size.width * .65f, size.height * .25f)
            quadraticTo(size.width * .86f, size.height * .30f, size.width * .82f, size.height * .55f)
            lineTo(size.width * .82f, size.height * .68f)
        }
        onDrawBehind {
            drawRect(
                color,
                topLeft = Offset(size.width * .17f, size.height * .12f),
                size = Size(size.width * .48f, size.height * .68f),
                style = stroke,
            )
            drawLine(color, Offset(size.width * .12f, size.height * .82f), Offset(size.width * .72f, size.height * .82f), stroke.width)
            drawLine(color, Offset(size.width * .25f, size.height * .30f), Offset(size.width * .57f, size.height * .30f), stroke.width)
            drawPath(hose, color, style = stroke)
        }
    }) { }
}

private fun formatDashboardInteger(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value)

@Composable
private fun MiniValue(
    label: String,
    value: String,
    warning: Boolean,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            color = Color.White.copy(alpha = .50f),
            fontSize = labelSize,
            lineHeight = labelSize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            color = if (warning) SLineRed else Color.White,
            fontSize = valueSize,
            lineHeight = valueSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LauncherSettingsOverlay(
    headerHeight: androidx.compose.ui.unit.Dp,
    selectedTab: LauncherSettingsTab,
    onSelectedTabChanged: (LauncherSettingsTab) -> Unit,
    diagnostics: MapDiagnostics,
    mapTileStyle: MapTileStyle,
    onMapTileStyleChanged: (MapTileStyle) -> Unit,
    mapColorMode: MapColorMode,
    onMapColorModeChanged: (MapColorMode) -> Unit,
    mapCacheLimit: MapCacheLimit,
    onMapCacheLimitChanged: (MapCacheLimit) -> Unit,
    mapDebugEnabled: Boolean,
    onMapDebugChanged: (Boolean) -> Unit,
    maintenanceMessage: String,
    mapCacheSizeBytes: Long,
    debugLogStats: MapDebugLogStats,
    onClearMapCache: () -> Unit,
    onClearDebugLogs: () -> Unit,
    onExportDebugLogs: ((Boolean?) -> Unit) -> Unit,
    poiSnapshot: PoiSnapshot,
    poiNotice: FloatingNotification?,
    onImportPoiSource: () -> Unit,
    onImportPoiIcon: () -> Unit,
    onImportPoiCategories: () -> Unit,
    onDeletePoiSource: (String) -> Unit,
    onDeletePoiIcon: (String) -> Unit,
    onDeletePoiCategories: () -> Unit,
    assistantSettings: AssistantSettings,
    assistantCredentialTester: AssistantCredentialTester,
    readAssistantErrorLogStats: () -> AssistantErrorLogStats,
    onExportAssistantErrorLogs: ((Boolean?) -> Unit) -> Unit,
    onClearAssistantErrorLogs: ((Int) -> Unit) -> Unit,
    onAssistantSaved: () -> Unit,
    functionalEventLogAccess: FunctionalEventLogAccess?,
    updateState: LauncherUpdateState,
    onSelectUpdateApk: () -> Unit,
    onRequestDeviceReboot: () -> Result<Unit>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val installedAtEpochMs = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .lastUpdateTime
        }.getOrDefault(0L)
    }
    val locale = LocalConfiguration.current.locales[0]
    val buildDate = remember(locale) { formatBuildDate(BuildConfig.BUILD_TIME_EPOCH_MS, locale) }
    val installedDate = remember(installedAtEpochMs, locale) {
        formatBuildDate(installedAtEpochMs, locale)
    }
    val mapColorLabels = mapOf(
        MapColorMode.AUTOMATIC to stringResource(R.string.map_automatic).uppercase(locale),
        MapColorMode.LIGHT to stringResource(R.string.map_light).uppercase(locale),
        MapColorMode.DARK to stringResource(R.string.map_dark).uppercase(locale),
    )
    val mapDebugLabels = mapOf(
        false to stringResource(R.string.map_inactive).uppercase(locale),
        true to stringResource(R.string.map_active).uppercase(locale),
    )
    val languageLabels = mapOf(
        AppLanguage.SPANISH to stringResource(R.string.language_spanish).uppercase(locale),
        AppLanguage.ENGLISH to stringResource(R.string.language_english).uppercase(locale),
    )
    var showRebootConfirmation by remember { mutableStateOf(false) }
    var rebootRequestFailed by remember { mutableStateOf(false) }

    if (showRebootConfirmation) {
        AlertDialog(
            onDismissRequest = { showRebootConfirmation = false },
            title = { Text(stringResource(R.string.device_reboot_title)) },
            text = { Text(stringResource(R.string.device_reboot_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebootConfirmation = false
                        rebootRequestFailed = onRequestDeviceReboot().isFailure
                    },
                ) {
                    Text(
                        stringResource(R.string.device_reboot_confirm),
                        color = Color(0xFFFF6D00),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirmation = false }) {
                    Text(stringResource(R.string.device_reboot_cancel))
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LauncherOverlayHeader(stringResource(R.string.launcher_settings_title), headerHeight, onClose)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LauncherSettingsTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    Box(
                        Modifier.weight(1f).height(SettingsDimensions.TabHeight)
                            .clip(shape)
                            .background(
                                if (selected) SettingsPalette.Content else SettingsPalette.ShellControl
                            )
                            .clickable { onSelectedTabChanged(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(tab.labelRes).uppercase(LocalConfiguration.current.locales[0]),
                            color = if (selected) SettingsPalette.Accent else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(SettingsPalette.Content)
                    .padding(16.dp),
            ) {
                when (selectedTab) {
        LauncherSettingsTab.MAP -> Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.map_color).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                SegmentedSettingsSelector(
                    options = MapColorMode.entries,
                    selected = mapColorMode,
                    label = { mode -> mapColorLabels.getValue(mode) },
                    onSelected = onMapColorModeChanged,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.map_style).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                SegmentedSettingsSelector(
                    options = MapTileStyle.entries.filterNot { it == MapTileStyle.DARK },
                    selected = mapTileStyle,
                    label = MapTileStyle::displayName,
                    onSelected = onMapTileStyleChanged,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.map_cache_maximum).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                SegmentedSettingsSelector(
                    options = MapCacheLimit.entries,
                    selected = mapCacheLimit,
                    label = MapCacheLimit::displayName,
                    onSelected = onMapCacheLimitChanged,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                SettingsStackedValue(stringResource(R.string.map_label), diagnostics.mapStatus)
                SettingsStackedValue(
                    stringResource(R.string.map_network),
                    stringResource(if (diagnostics.networkAvailable) R.string.map_connected else R.string.map_offline),
                )
                SettingsStackedValue(
                    stringResource(R.string.map_gps),
                    if (diagnostics.gpsAvailable) {
                        stringResource(R.string.map_gps_active)
                    } else if (diagnostics.hasKnownPosition) {
                        stringResource(R.string.map_gps_last_position)
                    } else {
                        stringResource(R.string.map_gps_no_position)
                    },
                )
                val coordinates = if (
                    diagnostics.latitude != null && diagnostics.longitude != null
                ) {
                    String.format(
                        java.util.Locale.US,
                        "%.5f, %.5f",
                        diagnostics.latitude,
                        diagnostics.longitude,
                    )
                } else {
                    "—"
                }
                SettingsStackedValue(stringResource(R.string.map_coordinates), coordinates)
                SettingsStackedValue(stringResource(R.string.map_provider), "OpenFreeMap / OpenStreetMap")
                if (diagnostics.mapError.isNotBlank()) {
                    SettingsStackedValue(stringResource(R.string.map_last_error), diagnostics.mapError)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                SettingsStorageUsage(
                    label = stringResource(R.string.map_cache),
                    usedBytes = mapCacheSizeBytes,
                    limitBytes = mapCacheLimit.bytes,
                )
                Spacer(Modifier.height(7.dp))
                SettingsActionButton(
                    stringResource(R.string.map_clear_cache),
                    SettingsDimensions.ActionHeight,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClearMapCache,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.map_logs).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsActionButton(
                        stringResource(
                            R.string.map_download_logs,
                            debugLogStats.fileCount,
                            formatStorageSize(debugLogStats.sizeBytes),
                        ),
                        SettingsDimensions.ActionHeight,
                        modifier = Modifier.weight(1f),
                    ) {
                        onExportDebugLogs { }
                    }
                    SettingsActionButton(
                        stringResource(R.string.map_clear_logs),
                        SettingsDimensions.ActionHeight,
                        modifier = Modifier.weight(1f),
                        destructive = true,
                        onClick = onClearDebugLogs,
                    )
                }
                if (maintenanceMessage.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        maintenanceMessage,
                        color = SettingsPalette.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.map_diagnostics).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                SegmentedSettingsSelector(
                    options = listOf(false, true),
                    selected = mapDebugEnabled,
                    label = { enabled -> mapDebugLabels.getValue(enabled) },
                    onSelected = onMapDebugChanged,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsPalette.Card)
                    .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.poi_title).uppercase(LocalConfiguration.current.locales[0]),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SettingsActionButton(
                        stringResource(R.string.poi_import_geojson),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        onClick = onImportPoiSource,
                    )
                    SettingsActionButton(
                        stringResource(R.string.poi_import_png),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        onClick = onImportPoiIcon,
                    )
                    SettingsActionButton(
                        stringResource(R.string.poi_import_categories),
                        SettingsDimensions.ActionHeight,
                        Modifier.weight(1f),
                        onClick = onImportPoiCategories,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.poi_summary,
                        poiSnapshot.sources.size,
                        poiSnapshot.totalPoints,
                        poiSnapshot.categoryCatalog?.styles?.size ?: 0,
                        poiSnapshot.icons.size,
                    ),
                    color = SettingsPalette.MutedText,
                    fontSize = 9.sp,
                )
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    poiSnapshot.categoryCatalog?.let { catalog ->
                        item(key = "categories") {
                            PoiItemRow("${catalog.fileName} · ${catalog.styles.size}") {
                                onDeletePoiCategories()
                            }
                        }
                    }
                    lazyListItems(
                        items = poiSnapshot.sources,
                        key = { source -> "source-${source.fileName}" },
                    ) { source ->
                        PoiItemRow("${source.fileName} · ${source.pointCount}") {
                            onDeletePoiSource(source.fileName)
                        }
                    }
                    lazyListItems(
                        items = poiSnapshot.icons,
                        key = { icon -> "icon-${icon.code}" },
                    ) { icon ->
                        PoiItemRow("${icon.code}.png · ${icon.width}×${icon.height}") {
                            onDeletePoiIcon(icon.code)
                        }
                    }
                    if (
                        poiSnapshot.sources.isEmpty() &&
                        poiSnapshot.icons.isEmpty() &&
                        poiSnapshot.categoryCatalog == null
                    ) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.poi_empty),
                                color = SettingsPalette.MutedText,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            )
                        }
                    }
                }
            }
        }
        LauncherSettingsTab.ASSISTANT -> AssistantSettingsPanel(
            settings = assistantSettings,
            credentialTester = assistantCredentialTester,
            readErrorLogStats = readAssistantErrorLogStats,
            onExportErrorLogs = onExportAssistantErrorLogs,
            onClearErrorLogs = onClearAssistantErrorLogs,
            onSaved = onAssistantSaved,
            modifier = Modifier.fillMaxSize(),
        )
        LauncherSettingsTab.LOGS -> FunctionalLogsPanel(
            access = functionalEventLogAccess,
            modifier = Modifier.fillMaxSize(),
        )
        LauncherSettingsTab.SYSTEM -> Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val updateMessage = when (updateState) {
                LauncherUpdateState.Idle -> null
                LauncherUpdateState.Preparing -> stringResource(R.string.launcher_update_preparing)
                is LauncherUpdateState.PermissionRequired -> stringResource(
                    R.string.launcher_update_permission_required,
                )
                LauncherUpdateState.Unreadable -> stringResource(R.string.launcher_update_unreadable)
                LauncherUpdateState.TooLarge -> stringResource(R.string.launcher_update_too_large)
                LauncherUpdateState.InvalidApk -> stringResource(R.string.launcher_update_invalid_apk)
                LauncherUpdateState.WrongApplication -> stringResource(
                    R.string.launcher_update_wrong_application,
                )
                LauncherUpdateState.InstallerUnavailable -> stringResource(
                    R.string.launcher_update_installer_unavailable,
                )
            }
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.system_application))
                Spacer(Modifier.height(10.dp))
                SettingsInlineValue(stringResource(R.string.system_version), BuildConfig.VERSION_NAME, 100.dp)
                SettingsInlineValue(stringResource(R.string.system_build), buildDate, 100.dp)
                SettingsInlineValue(stringResource(R.string.system_installation), installedDate, 100.dp)
                Spacer(Modifier.height(8.dp))
                SettingsSectionTitle(stringResource(R.string.system_language))
                Spacer(Modifier.height(6.dp))
                SegmentedSettingsSelector(
                    options = AppLanguage.entries,
                    selected = AppLanguageManager.current(context),
                    label = { language -> languageLabels.getValue(language) },
                    onSelected = { AppLanguageManager.select(context, it) },
                )
            }
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.system_update))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.system_update_description),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.weight(1f))
                if (updateMessage != null) {
                    Text(
                        updateMessage,
                        color = SettingsPalette.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SettingsActionButton(
                    text = stringResource(
                        if (updateState == LauncherUpdateState.Preparing) {
                            R.string.launcher_update_preparing
                        } else {
                            R.string.launcher_update_button
                        },
                    ),
                    controlHeight = SettingsDimensions.ActionHeight,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = updateState != LauncherUpdateState.Preparing,
                    onClick = onSelectUpdateApk,
                )
            }
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SettingsSectionTitle(stringResource(R.string.system_device))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.system_reboot_description),
                    color = SettingsPalette.MutedText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.weight(1f))
                if (rebootRequestFailed) {
                    Text(
                        stringResource(R.string.device_reboot_failed),
                        color = SettingsPalette.Danger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SettingsActionButton(
                    text = stringResource(R.string.device_reboot_button),
                    controlHeight = SettingsDimensions.ActionHeight,
                    modifier = Modifier.fillMaxWidth(),
                    destructive = true,
                    onClick = {
                        rebootRequestFailed = false
                        showRebootConfirmation = true
                    },
                )
            }
        }
                }
                FloatingNotificationHost(
                    notification = poiNotice.takeIf { selectedTab == LauncherSettingsTab.MAP },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .zIndex(2f),
                )
            }
        }
    }
}

@Composable
private fun PoiItemRow(label: String, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = SettingsPalette.Text,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.poi_delete),
            color = SLineRed,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onDelete).padding(5.dp),
        )
    }
}

internal enum class LauncherSettingsTab(val labelRes: Int) {
    MAP(R.string.launcher_settings_tab_map),
    ASSISTANT(R.string.launcher_settings_tab_assistant),
    LOGS(R.string.launcher_settings_tab_logs),
    SYSTEM(R.string.launcher_settings_tab_system),
}

@Composable
private fun <T> SegmentedSettingsSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit,
) = SettingsSegmentedSelector(
    options = options,
    selected = selected,
    label = label,
    controlHeight = SettingsDimensions.SelectorHeight,
    modifier = modifier,
    onSelected = onSelected,
)

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SettingsPalette.Card)
            .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text.uppercase(locale),
        color = SettingsPalette.Text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun SettingsInlineValue(
    label: String,
    value: String,
    labelWidth: androidx.compose.ui.unit.Dp = 112.dp,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${label.uppercase(locale)}:",
            color = SettingsPalette.MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            value,
            color = SettingsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsStackedValue(label: String, value: String) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label.uppercase(locale),
            color = SettingsPalette.MutedText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            color = SettingsPalette.Text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsStorageUsage(
    label: String,
    usedBytes: Long,
    limitBytes: Long,
) {
    val fraction = if (limitBytes > 0L) {
        (usedBytes.toDouble() / limitBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = SettingsPalette.MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${formatStorageSize(usedBytes)} / ${formatStorageSize(limitBytes)}",
            color = SettingsPalette.Text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(7.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SettingsPalette.Control),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(SettingsPalette.Accent),
        )
    }
}

internal fun formatBuildDate(
    epochMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    if (epochMillis <= 0L) return "—"
    val date = Date(epochMillis)
    val localizedDate = DateFormat.getDateInstance(DateFormat.SHORT, locale).apply {
        this.timeZone = timeZone
    }.format(date)
    val time24Hours = SimpleDateFormat("HH:mm", locale).apply {
        this.timeZone = timeZone
    }.format(date)
    return "$localizedDate $time24Hours"
}

@Composable
private fun AppsOverlay(
    apps: List<AppInfo>,
    headerHeight: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LauncherOverlayHeader(stringResource(R.string.launcher_apps_title), headerHeight, onClose)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SettingsPalette.Content)
                .padding(18.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(apps) { app ->
                    AppCard(
                        app = app,
                        onClick = { onLaunch(app.packageName) },
                        onLongClick = { onAppInfo(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherOverlayHeader(
    title: String,
    headerHeight: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(Color.Black)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.launcher_back).uppercase(LocalConfiguration.current.locales[0]),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClose)
                .background(SettingsPalette.ShellControl)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        )
        Spacer(Modifier.width(22.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun AppCard(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val painter = rememberDrawablePainter(app.icon)
    Column(
        Modifier
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsPalette.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.app_info_description, app.label),
                onLongClick = onLongClick,
            )
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (painter != null) {
            Image(painter, app.label, Modifier.size(45.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            app.label,
            color = SettingsPalette.Text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    if (drawable == null) return null
    return remember(drawable) {
        runCatching { BitmapPainter(drawable.toBitmap().asImageBitmap()) }.getOrNull()
    }
}
