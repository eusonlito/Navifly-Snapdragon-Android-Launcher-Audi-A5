package com.lito.a5launcher.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lito.a5launcher.A5_FUEL_TANK_CAPACITY_LITRES
import com.lito.a5launcher.AppLanguage
import com.lito.a5launcher.AppLanguageManager
import com.lito.a5launcher.BuildConfig
import com.lito.a5launcher.DeviceRebootAction
import com.lito.a5launcher.JourneyHistorySnapshot
import com.lito.a5launcher.JourneyStatisticsSnapshot
import com.lito.a5launcher.LauncherUpdateInstaller
import com.lito.a5launcher.LauncherViewModel
import com.lito.a5launcher.R
import com.lito.a5launcher.assistant.AssistantController
import com.lito.a5launcher.assistant.AssistantConversationDialog
import com.lito.a5launcher.assistant.AssistantCredentialTester
import com.lito.a5launcher.assistant.AssistantErrorLogStats
import com.lito.a5launcher.assistant.AssistantProvider
import com.lito.a5launcher.assistant.AssistantRobotButton
import com.lito.a5launcher.assistant.AssistantSettings
import com.lito.a5launcher.assistant.AssistantSettingsPanel
import com.lito.a5launcher.assistant.AssistantState
import com.lito.a5launcher.assistant.AssistantStatusPanel
import com.lito.a5launcher.functional.FunctionalEventLogAccess
import com.lito.a5launcher.location.LocationRepository
import com.lito.a5launcher.model.AppInfo
import com.lito.a5launcher.model.DoorStatus
import com.lito.a5launcher.ui.home.*
import com.lito.a5launcher.ui.theme.*
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOP_COMMAND_ORDER_KEY = "top_command_order"
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

internal fun parseFooterBlockOrder(
    serialized: String?,
    defaults: List<FooterBlockItem> = DefaultFooterBlockOrder,
): List<FooterBlockItem> {
    val stored = serialized.orEmpty().split(',').mapNotNull { name ->
        FooterBlockItem.entries.firstOrNull { it.name == name }
    }.distinct()
    return stored + defaults.filterNot(stored::contains)
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
    (litres.toFloat() / A5_FUEL_TANK_CAPACITY_LITRES).coerceIn(0f, 1f)

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
    val speedState = viewModel.speed.collectAsStateWithLifecycle()
    val rpmState = viewModel.rpm.collectAsStateWithLifecycle()
    val doorsState = viewModel.doorStatus.collectAsStateWithLifecycle()
    val fuelState = viewModel.fuel.collectAsStateWithLifecycle()
    val mileageState = viewModel.mileage.collectAsStateWithLifecycle()
    val rangeState = viewModel.range.collectAsStateWithLifecycle()
    val outsideState = viewModel.outsideTemp.collectAsStateWithLifecycle()
    val gearState = viewModel.gear.collectAsStateWithLifecycle()
    val tripStatisticsState = viewModel.tripStatistics.collectAsStateWithLifecycle()
    val tripStatistics by tripStatisticsState
    val partialStatisticsState = viewModel.partialStatistics.collectAsStateWithLifecycle()
    val partialStatistics by partialStatisticsState
    val totalStatistics by viewModel.totalStatistics.collectAsStateWithLifecycle()
    val journeyHistory by viewModel.journeyHistory.collectAsStateWithLifecycle()
    val pendingRefuelConfirmation by
        viewModel.pendingRefuelConfirmation.collectAsStateWithLifecycle()
    val navigationLaunchLocked by viewModel.navigationLaunchLocked.collectAsStateWithLifecycle()
    val seatbeltState = viewModel.seatbelt.collectAsStateWithLifecycle()
    val brakeState = viewModel.parkingBrake.collectAsStateWithLifecycle()
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
    // Derived holders: only the clock text recomposes when the minute changes.
    val timeState = remember(dashboardLocale) {
        derivedStateOf { SimpleDateFormat("HH:mm", dashboardLocale).format(Date(clockNow)) }
    }
    val dateState = remember(dashboardLocale) {
        derivedStateOf {
            DateFormat.getDateInstance(DateFormat.FULL, dashboardLocale).format(Date(clockNow))
        }
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
    var statisticsPanel by remember { mutableStateOf<StatisticsPanelScope?>(null) }
    var showManualPartialResetConfirmation by remember { mutableStateOf(false) }
    // Settings reopen on the tab the user last looked at, also after a restart.
    var launcherSettingsTab by remember {
        mutableStateOf(parseLauncherSettingsTab(launcherPreferences.getString(LAUNCHER_SETTINGS_TAB_KEY, null)))
    }
    LaunchedEffect(launcherSettingsTab) {
        launcherPreferences.edit { putString(LAUNCHER_SETTINGS_TAB_KEY, launcherSettingsTab.name) }
    }
    val topCommandOrderState = remember {
        mutableStateOf(parseTopCommandOrder(launcherPreferences.getString(TOP_COMMAND_ORDER_KEY, null)))
    }
    var topCommandOrder by topCommandOrderState

    LaunchedEffect(pendingRefuelConfirmation) {
        if (pendingRefuelConfirmation != null) showManualPartialResetConfirmation = false
    }
    val bootCount = remember {
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)
    }
    fun saveTheme(selection: HomeThemeSelection) {
        launcherPreferences.edit {
            putString(HOME_THEME_KEY, selection.theme.name)
            if (selection.randomBoot == null) remove(RANDOM_THEME_BOOT_KEY)
            else putInt(RANDOM_THEME_BOOT_KEY, selection.randomBoot)
        }
    }
    var themeSelection by remember {
        val saved = HomeThemeSelection(
            parseHomeTheme(launcherPreferences.getString(HOME_THEME_KEY, null)),
            launcherPreferences.getInt(RANDOM_THEME_BOOT_KEY, -1).takeIf { it >= 0 },
        )
        val current = saved.forBoot(bootCount)
        if (current != saved) saveTheme(current)
        mutableStateOf(current)
    }
    val homeTheme = themeSelection.theme
    // Each theme arranges the blocks differently, so each keeps its own order.
    fun storedFooterBlockOrder(theme: HomeTheme) = parseFooterBlockOrder(
        launcherPreferences.getString(footerBlockOrderKey(theme), null),
        defaultFooterBlockOrder(theme),
    )
    val footerBlockOrderState = remember { mutableStateOf(storedFooterBlockOrder(homeTheme)) }
    var footerBlockOrder by footerBlockOrderState
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
    val effectiveTileStyle = resolveMapTileStyle(
        colorMode = mapColorMode,
        vehicleLightsOn = delayedVehicleLights.effectiveLightsOn,
        systemNight = systemNight,
        preferredLightStyle = mapTileStyle,
    )
    val darkModeActive = effectiveTileStyle == MapTileStyle.DARK
    val mapStyleState = rememberUpdatedState(effectiveTileStyle)
    val startupProgress = rememberOemStartupProgress()
    var cacheGeneration by remember { mutableIntStateOf(0) }
    var maintenanceSizeGeneration by remember { mutableIntStateOf(0) }
    var maintenanceMessage by remember { mutableStateOf("") }
    var mapCacheSizeBytes by remember { mutableLongStateOf(0L) }
    var debugLogStats by remember { mutableStateOf(MapDebugLogStats(0, 0L)) }
    var mapDiagnostics by remember { mutableStateOf(MapDiagnostics()) }
    // Diagnostics change on every GPS fix; only the log path may restart effects here.
    val mapDebugLogPath by remember { derivedStateOf { mapDiagnostics.debugLogPath } }
    val coroutineScope = rememberCoroutineScope()
    val launchNavigation = { viewModel.launchWaze() }
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
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val pendingFile = pendingInstallPath?.let(::File)
        if (pendingFile != null && LauncherUpdateInstaller.canInstallPackages(context)) {
            pendingInstallPath = null
            updateState = if (
                LauncherUpdateInstaller.install(context, pendingFile).isFailure
            ) {
                LauncherUpdateState.InstallerUnavailable
            } else {
                LauncherUpdateState.Idle
            }
        } else if (pendingFile != null) {
            updateState = LauncherUpdateState.PermissionRequired(pendingFile)
        }
    }
    val onPreparedUpdate: (LauncherUpdateInstaller.PreparationResult) -> Unit = { preparation ->
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
                    pendingInstallPath = preparation.file.absolutePath
                    updateState = LauncherUpdateState.PermissionRequired(preparation.file)
                    if (runCatching {
                        installPermissionLauncher.launch(LauncherUpdateInstaller.requestPermissionIntent(context))
                    }.isFailure) updateState = LauncherUpdateState.InstallerUnavailable
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
    val updateApkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { selectedApk ->
        if (selectedApk != null) {
            updateState = LauncherUpdateState.Preparing
            coroutineScope.launch {
                onPreparedUpdate(withContext(Dispatchers.IO) {
                    LauncherUpdateInstaller.prepare(context, selectedApk)
                })
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
        mapDebugLogPath,
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

    // Pearl dims as soon as the headlights turn on, even with a fixed light map.
    val homeTokens = remember(homeTheme, darkModeActive) {
        homeTokensFor(homeTheme, dark = darkModeActive)
    }
    val homeTokensState = rememberUpdatedState(homeTokens)
    val darkModeActiveState = rememberUpdatedState(darkModeActive)
    val homeLightsActiveState = rememberUpdatedState(darkModeActive || (homeTheme == HomeTheme.BLUE_LIGHT && lightsOn == true))
    val assistantEnabledState = rememberUpdatedState(
        assistantController.settings.provider != AssistantProvider.DISABLED,
    )
    val currentStartAssistantTurn by rememberUpdatedState(startAssistantTurn)
    val homeState = remember {
        HomeState(
            speed = speedState,
            rpm = rpmState,
            gear = gearState,
            doors = doorsState,
            fuel = fuelState,
            mileage = mileageState,
            range = rangeState,
            outside = outsideState,
            seatbeltAlert = derivedStateOf { !seatbeltState.value },
            parkingBrake = brakeState,
            // The original footer lit the witness with the effective dark mode.
            lightsActive = homeLightsActiveState,
            trip = tripStatisticsState,
            partial = partialStatisticsState,
            time = timeState,
            date = dateState,
            startupProgress = startupProgress,
            topCommandOrder = topCommandOrderState,
            footerBlockOrder = footerBlockOrderState,
            assistantEnabled = assistantEnabledState,
            assistantActive = derivedStateOf {
                assistantUi.status !is AssistantState.Disabled &&
                    assistantUi.status !is AssistantState.Ready
            },
            navigationEnabled = derivedStateOf { !navigationLaunchLocked },
        )
    }
    val homeActions = remember(viewModel) {
        HomeActions(
            onNavigation = launchNavigation,
            onAssistant = { currentStartAssistantTurn() },
            onApps = { showApps = true },
            onLauncherSettings = { showLauncherSettings = true },
            onDeviceSettings = viewModel::launchSettings,
            onRecents = viewModel::openRecentApps,
            onMmi = viewModel::launchOriginalMMI,
            onStatistics = { scope ->
                statisticsPanel = toggleStatisticsPanel(current = statisticsPanel, requested = scope)
            },
            onTopCommandOrderChanged = { order ->
                if (order !== topCommandOrder) {
                    topCommandOrder = order
                    launcherPreferences.edit {
                        putString(TOP_COMMAND_ORDER_KEY, order.joinToString(",") { it.name })
                    }
                }
            },
            onFooterBlockOrderChanged = { order ->
                if (order !== footerBlockOrder) {
                    footerBlockOrder = order
                    launcherPreferences.edit {
                        putString(footerBlockOrderKey(themeSelection.theme), order.joinToString(",") { it.name })
                    }
                }
            },
        )
    }
    var poiWarningVisible by remember { mutableStateOf(false) }
    // A single movable map: switching themes moves the MapView instead of rebuilding it.
    val mapContent = remember {
        movableContentOf { mapModifier: Modifier, viewport: MapViewportSpec ->
            CockpitMap(
                modifier = mapModifier,
                effectiveTileStyle = mapStyleState.value,
                preferredLightStyle = mapTileStyle,
                cacheLimit = mapCacheLimit,
                cacheGeneration = cacheGeneration,
                poiSnapshot = poiSnapshot,
                poiRepository = poiRepository,
                locationRepository = locationRepository,
                debugEnabled = mapDebugEnabled,
                colors = homeTokensState.value.map,
                viewport = viewport,
                topOverlay = {
                    AssistantStatusPanel(
                        state = assistantUi.status,
                        audioLevel = assistantUi.audioLevel,
                        action = assistantUi.action,
                        heardText = assistantUi.heardText,
                        modifier = Modifier.graphicsLayer {
                            alpha = oemStartupPresentation(startupProgress.value).contentAlpha
                        },
                    )
                },
                onDiagnosticsChanged = { mapDiagnostics = it },
                onPoiWarningChanged = { poiWarningVisible = it },
            )
        }
    }
    val homeSlots = remember {
        HomeSlots(
            map = { mapModifier, viewport -> mapContent(mapModifier, viewport) },
            mapOverlay = { contentPadding ->
                statisticsPanel?.let { scope ->
                    key(scope) {
                        JourneyStatisticsPanel(
                            title = stringResource(
                                when (scope) {
                                    StatisticsPanelScope.TRIP -> R.string.trip_statistics_title
                                    StatisticsPanelScope.PARTIAL -> R.string.partial_statistics_title
                                    StatisticsPanelScope.TOTAL -> R.string.total_statistics_title
                                },
                            ),
                            statistics = when (scope) {
                                StatisticsPanelScope.TRIP -> tripStatistics
                                StatisticsPanelScope.PARTIAL -> partialStatistics
                                StatisticsPanelScope.TOTAL -> totalStatistics
                            },
                            history = remember(journeyHistory.records, scope) {
                                statisticsPanelHistory(journeyHistory.records, scope)
                            },
                            startedAtEpochMs = when (scope) {
                                StatisticsPanelScope.TRIP -> journeyHistory.tripStartedAtEpochMs
                                StatisticsPanelScope.PARTIAL -> journeyHistory.partialStartedAtEpochMs
                                StatisticsPanelScope.TOTAL -> null
                            },
                            locale = LocalConfiguration.current.locales[0],
                            darkModeActive = darkModeActiveState.value,
                            onClose = { statisticsPanel = null },
                            onReset = if (scope == StatisticsPanelScope.PARTIAL) {
                                { showManualPartialResetConfirmation = true }
                            } else null,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val commandBarHeight = maxHeight * .125f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val tripArt = rememberTripArt(homeTheme, speedState) {
                startupProgress.value == null && !poiWarningVisible &&
                    !showApps && !showLauncherSettings && statisticsPanel == null &&
                    pendingRefuelConfirmation == null && !showManualPartialResetConfirmation &&
                    !homeState.assistantActive.value
            }
            CompositionLocalProvider(
                LocalHomeTokens provides homeTokens,
                LocalIllustratedNight provides darkModeActive,
                LocalTripArt provides tripArt,
            ) {
                when (homeTheme) {
                    HomeTheme.CLASSIC -> ClassicHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.HORIZON -> HorizonHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.FACETS -> FacetsHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.TELEMETRY -> TelemetryHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.SUBMARINE -> IllustratedHomeLayout(IllustratedStyle.SUBMARINE, homeState, homeActions, homeSlots)
                    HomeTheme.MOON -> IllustratedHomeLayout(IllustratedStyle.MOON, homeState, homeActions, homeSlots)
                    HomeTheme.GALAXY -> GalaxyHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.ADVENTURE -> AdventureHomeLayout(homeState, homeActions, homeSlots)
                    HomeTheme.BLUE_LIGHT -> BlueHomeLayout(homeState, homeActions, homeSlots, dark = darkModeActive)
                }
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
                    darkModeActive = darkModeActive,
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
                    darkModeActive = darkModeActive,
                    selectedTheme = themeSelection.theme.takeIf { themeSelection.randomBoot == null },
                    onHomeThemeChanged = { theme ->
                        val selection = when {
                            theme != null -> HomeThemeSelection(theme)
                            themeSelection.randomBoot == bootCount -> themeSelection
                            else -> HomeThemeSelection.random(bootCount)
                        }
                        saveTheme(selection)
                        themeSelection = selection
                        footerBlockOrder = storedFooterBlockOrder(selection.theme)
                        // Choosing a theme is the whole task: go straight back to the home screen.
                        showLauncherSettings = false
                    },
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
                    onPoiDistanceChanged = { category, metres ->
                        coroutineScope.async {
                            runCatching { poiRepository.setAlertDistance(category, metres) }.fold(
                                onSuccess = { poiSnapshot = it; true },
                                onFailure = {
                                    poiNotice = FloatingNotification(
                                        resources.getString(R.string.poi_distance_save_failed),
                                        FloatingNotificationTone.ERROR,
                                    )
                                    false
                                },
                            )
                        }.await()
                    },
                    onImportPoiSource = {
                        poiNotice = null
                        // Providers may report an unrelated MIME type; the importer validates name and content.
                        poiGeoJsonPicker.launch(arrayOf("*/*"))
                    },
                    onImportPoiIcon = {
                        poiNotice = null
                        poiIconPicker.launch(arrayOf("*/*"))
                    },
                    onImportPoiCategories = {
                        poiNotice = null
                        poiCategoriesPicker.launch(arrayOf("*/*"))
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
                    journeyHistory = journeyHistory,
                    tripStatistics = tripStatistics,
                    partialStatistics = partialStatistics,
                    onJourneyHistoryEnabledChanged = viewModel::setJourneyHistoryEnabled,
                    onDeleteJourneyHistoryRecord = viewModel::deleteJourneyHistoryRecord,
                    onClearJourneyHistory = viewModel::clearJourneyHistory,
                    updateState = updateState,
                    onPreparedUpdate = onPreparedUpdate,
                    onSelectUpdateApk = {
                        updateApkPicker.launch(arrayOf(LauncherUpdateInstaller.APK_MIME_TYPE))
                    },
                    onRequestDeviceReboot = { DeviceRebootAction.request(context) },
                    onClose = { showLauncherSettings = false },
                )
            }

            pendingRefuelConfirmation?.let {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.refuel_confirmation_title)) },
                    text = { Text(stringResource(R.string.refuel_confirmation_message)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmPendingRefuel) {
                            Text(
                                stringResource(R.string.refuel_confirmation_accept),
                                color = OemCockpitTokens.Cyan,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::keepPartialAfterRefuel) {
                            Text(stringResource(R.string.refuel_confirmation_reject))
                        }
                    },
                )
            }

            if (pendingRefuelConfirmation == null && showManualPartialResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showManualPartialResetConfirmation = false },
                    title = { Text(stringResource(R.string.partial_statistics_reset_title)) },
                    text = { Text(stringResource(R.string.partial_statistics_reset_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showManualPartialResetConfirmation = false
                                viewModel.resetPartialStatistics()
                            },
                        ) {
                            Text(
                                stringResource(R.string.partial_statistics_reset_confirm),
                                color = SettingsPalette.Danger,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showManualPartialResetConfirmation = false },
                        ) {
                            Text(stringResource(R.string.partial_statistics_reset_cancel))
                        }
                    },
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

internal enum class StatisticsPanelScope { TRIP, PARTIAL, TOTAL }

internal fun toggleStatisticsPanel(
    current: StatisticsPanelScope?,
    requested: StatisticsPanelScope,
): StatisticsPanelScope? = requested.takeUnless { it == current }

internal fun formatTripDuration(elapsedRealtimeMs: Long): String {
    val totalMinutes = elapsedRealtimeMs.coerceAtLeast(0L) / 60_000L
    return String.format(
        java.util.Locale.US,
        "%02d:%02d",
        totalMinutes / 60L,
        totalMinutes % 60L,
    )
}

internal fun formatOneDecimal(
    value: Double,
    locale: java.util.Locale = java.util.Locale.getDefault(),
): String = String.format(locale, "%.1f", value.takeIf { it.isFinite() } ?: 0.0)

@Composable
private fun LauncherSettingsOverlay(
    headerHeight: androidx.compose.ui.unit.Dp,
    darkModeActive: Boolean,
    selectedTheme: HomeTheme?,
    onHomeThemeChanged: (HomeTheme?) -> Unit,
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
    onPoiDistanceChanged: suspend (String, Int) -> Boolean,
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
    journeyHistory: JourneyHistorySnapshot,
    tripStatistics: JourneyStatisticsSnapshot,
    partialStatistics: JourneyStatisticsSnapshot,
    onJourneyHistoryEnabledChanged: (Boolean) -> Unit,
    onDeleteJourneyHistoryRecord: (String) -> Unit,
    onClearJourneyHistory: () -> Unit,
    updateState: LauncherUpdateState,
    onPreparedUpdate: (LauncherUpdateInstaller.PreparationResult) -> Unit,
    onSelectUpdateApk: () -> Unit,
    onRequestDeviceReboot: () -> Result<Unit>,
    onClose: () -> Unit,
) {
    var statisticsOpen by rememberSaveable { mutableStateOf(false) }
    var poiCategoriesOpen by rememberSaveable { mutableStateOf(false) }
    val historyState = rememberSaveableStateHolder()
    BackHandler(enabled = statisticsOpen || poiCategoriesOpen) { statisticsOpen = false; poiCategoriesOpen = false }
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
    val languageLabels = mapOf(
        AppLanguage.SPANISH to stringResource(R.string.language_spanish),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
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
            .background(SettingsPalette.Background)
    ) {
        LauncherOverlayHeader(
            title = stringResource(R.string.launcher_settings_title),
            headerHeight = headerHeight,
            darkModeActive = darkModeActive,
            onClose = {
                when {
                    statisticsOpen -> statisticsOpen = false
                    poiCategoriesOpen -> poiCategoriesOpen = false
                    else -> onClose()
                }
            },
        ) {
            LauncherSettingsTabs(
                selectedTab = selectedTab,
                onSelectedTabChanged = {
                    statisticsOpen = false
                    poiCategoriesOpen = false
                    onSelectedTabChanged(it)
                },
                modifier = Modifier.weight(1f).padding(start = 40.dp),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
        ) {
            when (selectedTab) {
                LauncherSettingsTab.TRIPS -> if (statisticsOpen) {
                    PartialStatisticsPanel(records = journeyHistory.records, onBack = { statisticsOpen = false })
                } else historyState.SaveableStateProvider("journey-history") {
                    JourneyHistoryPanel(
                        snapshot = journeyHistory,
                        tripStatistics = tripStatistics,
                        partialStatistics = partialStatistics,
                        onEnabledChanged = onJourneyHistoryEnabledChanged,
                        onDeleteRecord = onDeleteJourneyHistoryRecord,
                        onClearAll = onClearJourneyHistory,
                        onOpenStatistics = { statisticsOpen = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                LauncherSettingsTab.MAP -> if (poiCategoriesOpen) {
                    PoiCategorySettingsPanel(poiSnapshot, onPoiDistanceChanged, onBack = { poiCategoriesOpen = false })
                } else MapSettingsPanel(
                    diagnostics = diagnostics,
                    mapTileStyle = mapTileStyle,
                    onMapTileStyleChanged = onMapTileStyleChanged,
                    mapColorMode = mapColorMode,
                    onMapColorModeChanged = onMapColorModeChanged,
                    mapCacheLimit = mapCacheLimit,
                    onMapCacheLimitChanged = onMapCacheLimitChanged,
                    mapDebugEnabled = mapDebugEnabled,
                    onMapDebugChanged = onMapDebugChanged,
                    maintenanceMessage = maintenanceMessage,
                    mapCacheSizeBytes = mapCacheSizeBytes,
                    debugLogStats = debugLogStats,
                    onClearMapCache = onClearMapCache,
                    onClearDebugLogs = onClearDebugLogs,
                    onExportDebugLogs = onExportDebugLogs,
                    poiSnapshot = poiSnapshot,
                    onImportPoiSource = onImportPoiSource,
                    onImportPoiIcon = onImportPoiIcon,
                    onImportPoiCategories = onImportPoiCategories,
                    onOpenPoiCategories = { poiCategoriesOpen = true },
                    onDeletePoiSource = onDeletePoiSource,
                    onDeletePoiIcon = onDeletePoiIcon,
                    onDeletePoiCategories = onDeletePoiCategories,
                )
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
                LauncherSettingsTab.THEMES -> ThemeSettingsPanel(
                    selected = selectedTheme,
                    onSelected = onHomeThemeChanged,
                    modifier = Modifier.fillMaxSize(),
                )
                LauncherSettingsTab.SYSTEM -> SystemSettingsPanel(
                    buildDate = buildDate,
                    installedDate = installedDate,
                    languageLabels = languageLabels,
                    updateState = updateState,
                    onPreparedUpdate = onPreparedUpdate,
                    onSelectUpdateApk = onSelectUpdateApk,
                    rebootRequestFailed = rebootRequestFailed,
                    onReboot = {
                        rebootRequestFailed = false
                        showRebootConfirmation = true
                    },
                )
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

@Composable
private fun LauncherSettingsTabs(
    selectedTab: LauncherSettingsTab,
    onSelectedTabChanged: (LauncherSettingsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherSettingsTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val contentColor = if (selected) SettingsPalette.OnAccent else SettingsPalette.Text.copy(alpha = .78f)
            Row(
                Modifier
                    .weight(1f)
                    .height(SettingsDimensions.TabHeight)
                    .clip(SettingsControlShape)
                    .background(if (selected) SettingsPalette.Accent else SettingsPalette.Card)
                    .border(
                        1.dp,
                        if (selected) SettingsPalette.Accent else SettingsPalette.Border,
                        SettingsControlShape,
                    )
                    .selectable(
                        selected = selected,
                        role = androidx.compose.ui.semantics.Role.Tab,
                        onClick = { onSelectedTabChanged(tab) },
                    )
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(tab.labelRes).uppercase(locale),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MapSettingsPanel(
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
    onImportPoiSource: () -> Unit,
    onImportPoiIcon: () -> Unit,
    onImportPoiCategories: () -> Unit,
    onOpenPoiCategories: () -> Unit,
    onDeletePoiSource: (String) -> Unit,
    onDeletePoiIcon: (String) -> Unit,
    onDeletePoiCategories: () -> Unit,
) {
    val mapColorLabels = mapOf(
        MapColorMode.AUTOMATIC to stringResource(R.string.map_automatic),
        MapColorMode.LIGHT to stringResource(R.string.map_light),
        MapColorMode.DARK to stringResource(R.string.map_dark),
    )
    val mapDebugLabels = mapOf(
        false to stringResource(R.string.map_inactive),
        true to stringResource(R.string.map_active),
    )
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.CardGap),
    ) {
        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            SettingsSectionTitle(stringResource(R.string.map_appearance), SettingsIcons.Palette)
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            SettingsLabel(stringResource(R.string.map_color))
            SegmentedSettingsSelector(
                options = MapColorMode.entries,
                selected = mapColorMode,
                label = { mode -> mapColorLabels.getValue(mode) },
                onSelected = onMapColorModeChanged,
            )
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            SettingsLabel(stringResource(R.string.map_style))
            SegmentedSettingsSelector(
                options = MapTileStyle.entries.filterNot { it == MapTileStyle.DARK },
                selected = mapTileStyle,
                label = MapTileStyle::displayName,
                onSelected = onMapTileStyleChanged,
            )
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            SettingsLabel(stringResource(R.string.map_cache_maximum))
            SegmentedSettingsSelector(
                options = MapCacheLimit.entries,
                selected = mapCacheLimit,
                label = MapCacheLimit::displayName,
                onSelected = onMapCacheLimitChanged,
            )
        }

        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            SettingsSectionTitle(stringResource(R.string.map_status), SettingsIcons.Activity)
            Spacer(Modifier.height(4.dp))
            val mapLoaded = diagnostics.mapError.isBlank()
            SettingsStackedValueRow(
                stringResource(R.string.map_label),
                diagnostics.mapStatus,
                icon = SettingsIcons.Map,
                statusColor = if (mapLoaded) SettingsPalette.Success else SettingsPalette.Danger,
            )
            SettingsStackedValueRow(
                stringResource(R.string.map_network),
                stringResource(if (diagnostics.networkAvailable) R.string.map_connected else R.string.map_offline),
                icon = SettingsIcons.Wifi,
                statusColor = if (diagnostics.networkAvailable) SettingsPalette.Success else SettingsPalette.Warning,
            )
            SettingsStackedValueRow(
                stringResource(R.string.map_gps),
                if (diagnostics.gpsAvailable) {
                    stringResource(R.string.map_gps_active)
                } else if (diagnostics.hasKnownPosition) {
                    stringResource(R.string.map_gps_last_position)
                } else {
                    stringResource(R.string.map_gps_no_position)
                },
                icon = SettingsIcons.Crosshair,
                statusColor = when {
                    diagnostics.gpsAvailable -> SettingsPalette.Success
                    diagnostics.hasKnownPosition -> SettingsPalette.Warning
                    else -> SettingsPalette.Danger
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
            SettingsStackedValueRow(stringResource(R.string.map_coordinates), coordinates, icon = SettingsIcons.Pin)
            SettingsStackedValueRow(
                stringResource(R.string.map_provider),
                "OpenFreeMap / OSM",
                icon = SettingsIcons.Globe,
                divider = diagnostics.mapError.isNotBlank(),
            )
            if (diagnostics.mapError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.map_last_error)}: ${diagnostics.mapError}",
                    color = SettingsPalette.Danger,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            SettingsSectionTitle(stringResource(R.string.map_cache), SettingsIcons.Database) {
                Text(
                    "${formatStorageSize(mapCacheSizeBytes)} / ${formatStorageSize(mapCacheLimit.bytes)}",
                    color = SettingsPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            SettingsStorageUsage(usedBytes = mapCacheSizeBytes, limitBytes = mapCacheLimit.bytes)
            Spacer(Modifier.height(12.dp))
            SettingsActionButton(
                stringResource(R.string.map_clear_cache),
                SettingsDimensions.ActionHeight,
                modifier = Modifier.fillMaxWidth(),
                icon = SettingsIcons.Trash,
                onClick = onClearMapCache,
            )
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            SettingsDivider()
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            SettingsSectionTitle(stringResource(R.string.map_logs), SettingsIcons.Document) {
                Text(
                    stringResource(
                        R.string.settings_files_summary,
                        debugLogStats.fileCount,
                        formatStorageSize(debugLogStats.sizeBytes),
                    ),
                    color = SettingsPalette.MutedText,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsActionButton(
                    stringResource(R.string.settings_download),
                    SettingsDimensions.ActionHeight,
                    modifier = Modifier.weight(1f),
                    enabled = debugLogStats.fileCount > 0,
                    icon = SettingsIcons.Download,
                ) {
                    onExportDebugLogs { }
                }
                SettingsActionButton(
                    stringResource(R.string.map_clear_logs),
                    SettingsDimensions.ActionHeight,
                    modifier = Modifier.weight(1f),
                    enabled = debugLogStats.fileCount > 0,
                    destructive = true,
                    icon = SettingsIcons.Trash,
                    onClick = onClearDebugLogs,
                )
            }
            if (maintenanceMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    maintenanceMessage,
                    color = SettingsPalette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            SettingsLabel(stringResource(R.string.map_diagnostics))
            SegmentedSettingsSelector(
                options = listOf(false, true),
                selected = mapDebugEnabled,
                label = { enabled -> mapDebugLabels.getValue(enabled) },
                onSelected = onMapDebugChanged,
            )
        }

        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            val poiItems = poiSnapshot.sources.size + poiSnapshot.icons.size +
                (if (poiSnapshot.categoryCatalog != null) 1 else 0)
            SettingsSectionTitle(stringResource(R.string.poi_title), SettingsIcons.Pin) {
                SettingsCountBadge(poiSnapshot.totalPoints.toString())
            }
            Spacer(Modifier.height(SettingsDimensions.SectionGap))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PoiImportTile(stringResource(R.string.poi_import_geojson), SettingsIcons.Upload, Modifier.weight(1f), onImportPoiSource)
                PoiImportTile(stringResource(R.string.poi_import_png), SettingsIcons.Image, Modifier.weight(1f), onImportPoiIcon)
                PoiImportTile(stringResource(R.string.poi_import_categories), SettingsIcons.Tag, Modifier.weight(1f), onImportPoiCategories)
            }
            Spacer(Modifier.height(8.dp))
            SettingsActionButton(
                stringResource(R.string.poi_alert_distances), SettingsDimensions.ActionHeight,
                modifier = Modifier.fillMaxWidth(), icon = SettingsIcons.Activity,
                onClick = onOpenPoiCategories,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.poi_summary,
                    poiSnapshot.sources.size,
                    poiSnapshot.totalPoints,
                    poiSnapshot.categoryCatalog?.styles?.size ?: 0,
                    poiSnapshot.icons.size,
                ),
                color = SettingsPalette.MutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (poiItems == 0) {
                SettingsEmptyState(
                    icon = SettingsIcons.Pin,
                    text = stringResource(R.string.poi_empty),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    poiSnapshot.categoryCatalog?.let { catalog ->
                        item(key = "categories") {
                            PoiItemRow(SettingsIcons.Tag, catalog.fileName, catalog.styles.size.toString()) {
                                onDeletePoiCategories()
                            }
                        }
                    }
                    lazyListItems(
                        items = poiSnapshot.sources,
                        key = { source -> "source-${source.fileName}" },
                    ) { source ->
                        PoiItemRow(SettingsIcons.Map, source.fileName, source.pointCount.toString()) {
                            onDeletePoiSource(source.fileName)
                        }
                    }
                    lazyListItems(
                        items = poiSnapshot.icons,
                        key = { icon -> "icon-${icon.code}" },
                    ) { icon ->
                        PoiItemRow(SettingsIcons.Image, "${icon.code}.png", "${icon.width}×${icon.height}") {
                            onDeletePoiIcon(icon.code)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsPanel(
    buildDate: String,
    installedDate: String,
    languageLabels: Map<AppLanguage, String>,
    updateState: LauncherUpdateState,
    onPreparedUpdate: (LauncherUpdateInstaller.PreparationResult) -> Unit,
    onSelectUpdateApk: () -> Unit,
    rebootRequestFailed: Boolean,
    onReboot: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.CardGap),
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
            SettingsSectionTitle(stringResource(R.string.system_application), SettingsIcons.Info)
            Spacer(Modifier.height(6.dp))
            SettingsValueRow(stringResource(R.string.system_version), BuildConfig.VERSION_NAME, icon = SettingsIcons.Tag)
            SettingsValueRow(stringResource(R.string.system_build), buildDate, icon = SettingsIcons.Package)
            SettingsValueRow(
                stringResource(R.string.system_installation),
                installedDate,
                icon = SettingsIcons.Download,
                divider = false,
            )
            Spacer(Modifier.weight(1f))
            SettingsSectionTitle(stringResource(R.string.system_language), SettingsIcons.Globe)
            Spacer(Modifier.height(10.dp))
            SegmentedSettingsSelector(
                options = AppLanguage.entries,
                selected = AppLanguageManager.current(context),
                label = { language -> languageLabels.getValue(language) },
                onSelected = { AppLanguageManager.select(context, it) },
            )
        }
        LauncherUpdateCard(
            busy = updateState == LauncherUpdateState.Preparing,
            installMessage = updateMessage,
            onPrepared = onPreparedUpdate,
            onSelectLocalApk = onSelectUpdateApk,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            SettingsSectionTitle(stringResource(R.string.system_device), SettingsIcons.Monitor)
            Spacer(Modifier.height(6.dp))
            SettingsValueRow(
                stringResource(R.string.system_device_model),
                listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" "),
                icon = SettingsIcons.Monitor,
            )
            SettingsValueRow(
                stringResource(R.string.system_android_version),
                "${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}",
                icon = SettingsIcons.Chip,
                divider = false,
            )
            Spacer(Modifier.weight(1f))
            if (rebootRequestFailed) {
                Text(
                    stringResource(R.string.device_reboot_failed),
                    color = SettingsPalette.Danger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }
            SettingsActionButton(
                text = stringResource(R.string.device_reboot_button),
                controlHeight = SettingsDimensions.ActionHeight,
                modifier = Modifier.fillMaxWidth(),
                destructive = true,
                icon = SettingsIcons.Power,
                onClick = onReboot,
            )
        }
    }
}

@Composable
private fun PoiImportTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(58.dp)
            .clip(SettingsControlShape)
            .background(SettingsPalette.Accent.copy(alpha = .08f))
            .border(1.dp, SettingsPalette.Accent.copy(alpha = .45f), SettingsControlShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = SettingsPalette.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = SettingsPalette.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PoiItemRow(icon: ImageVector, label: String, detail: String, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(SettingsControlShape)
            .background(SettingsPalette.Raised)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SettingsPalette.MutedText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = SettingsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            detail,
            color = SettingsPalette.MutedText,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        SettingsDeleteIconButton(
            contentDescription = stringResource(R.string.poi_delete),
            onClick = onDelete,
        )
    }
}

internal const val LAUNCHER_SETTINGS_TAB_KEY = "launcher_settings_tab"

/** The stored tab, or the first one when nothing (or an unknown name) is stored. */
internal fun parseLauncherSettingsTab(value: String?): LauncherSettingsTab =
    LauncherSettingsTab.entries.firstOrNull { it.name == value } ?: LauncherSettingsTab.TRIPS

internal enum class LauncherSettingsTab(val labelRes: Int) {
    TRIPS(R.string.launcher_settings_tab_trips),
    MAP(R.string.launcher_settings_tab_map),
    ASSISTANT(R.string.launcher_settings_tab_assistant),
    LOGS(R.string.launcher_settings_tab_logs),
    THEMES(R.string.launcher_settings_tab_themes),
    SYSTEM(R.string.launcher_settings_tab_system),
    ;

    val icon: ImageVector
        get() = when (this) {
            TRIPS -> SettingsIcons.Route
            MAP -> SettingsIcons.Map
            ASSISTANT -> SettingsIcons.Sparkles
            LOGS -> SettingsIcons.Document
            THEMES -> SettingsIcons.Palette
            SYSTEM -> SettingsIcons.Gear
        }
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
private fun SettingsStorageUsage(
    usedBytes: Long,
    limitBytes: Long,
) {
    val fraction = if (limitBytes > 0L) {
        (usedBytes.toDouble() / limitBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
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
                .clip(RoundedCornerShape(3.dp))
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
    darkModeActive: Boolean,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LauncherOverlayHeader(
            title = stringResource(R.string.launcher_apps_title),
            headerHeight = headerHeight,
            darkModeActive = darkModeActive,
            onClose = onClose,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SettingsPalette.Card)
                .border(1.dp, SettingsPalette.Border, RoundedCornerShape(12.dp))
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
    darkModeActive: Boolean,
    onClose: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
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
        PanelIconAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.launcher_back),
            tint = OemCockpitTokens.Cyan,
            darkModeActive = darkModeActive,
            onClick = onClose,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        trailing()
    }
}

@Composable
fun AppCard(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val painter = rememberDrawablePainter(app.icon)
    Column(
        Modifier
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsPalette.Raised)
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
