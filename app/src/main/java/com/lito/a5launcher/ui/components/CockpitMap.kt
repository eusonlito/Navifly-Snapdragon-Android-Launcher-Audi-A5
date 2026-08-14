package com.lito.a5launcher.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lito.a5launcher.R
import com.lito.a5launcher.location.LocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

private const val LOCATION_PREFS = "cockpit_map_location"
private const val POI_SOURCE_ID = "private-poi-sources"
private const val POI_PULSE_FIRST_LAYER_ID = "private-poi-pulse-first"
private const val POI_PULSE_SECOND_LAYER_ID = "private-poi-pulse-second"
private const val POI_ICON_LAYER_ID = "private-poi-icons"
private const val POI_DEFAULT_ICON_ID = "poi-default"
private const val POI_PULSE_ICON_ID = "poi-pulse"
internal const val POI_PULSE_DURATION_MS = 2_400L
private const val POI_PULSE_BASE_SIZE = 1.56f
private const val CAMERA_FRAME_INTERVAL_NANOS = 33_333_333L
private const val CAMERA_MINIMUM_PIXEL_MOVEMENT = .5
private const val CAMERA_MINIMUM_BEARING_CHANGE = .2f
internal const val MAP_MINIMUM_ZOOM = 0.0
internal const val MAP_MAXIMUM_ZOOM = 18.0
internal const val MAP_DEFAULT_ZOOM = 16.0

internal fun clampMapZoom(zoom: Double): Double =
    zoom.coerceIn(MAP_MINIMUM_ZOOM, MAP_MAXIMUM_ZOOM)

enum class MapTileStyle(
    val displayName: String,
    val cacheKey: String,
    val styleUrl: String,
) {
    POSITRON("POSITRON", "positron", "https://tiles.openfreemap.org/styles/positron"),
    LIBERTY("LIBERTY", "liberty", "https://tiles.openfreemap.org/styles/liberty"),
    BRIGHT("BRIGHT", "bright", "https://tiles.openfreemap.org/styles/bright"),
    DARK("DARK", "dark", "https://tiles.openfreemap.org/styles/dark"),
}

enum class MapColorMode(val displayName: String) {
    AUTOMATIC("AUTOMÁTICO"),
    LIGHT("CLARO"),
    DARK("OSCURO"),
}

internal fun resolveMapTileStyle(
    colorMode: MapColorMode,
    vehicleLightsOn: Boolean?,
    systemNight: Boolean,
    preferredLightStyle: MapTileStyle,
): MapTileStyle = when (colorMode) {
    MapColorMode.AUTOMATIC ->
        if (vehicleLightsOn ?: systemNight) MapTileStyle.DARK else preferredLightStyle
    MapColorMode.LIGHT -> preferredLightStyle
    MapColorMode.DARK -> MapTileStyle.DARK
}

/** OpenFreeMap's near-black defaults need more separation on the vehicle display. */
private object CockpitDarkPalette {
    const val BACKGROUND = "#151A1E"
    const val WATER = "#102B38"
    const val LAND = "#1A2025"
    const val GREEN = "#1B2922"
    const val BUILDING = "#283137"
    const val PATH = "#46535C"
    const val MINOR_ROAD = "#5E6C75"
    const val MAJOR_ROAD = "#94A3AC"
    const val MOTORWAY = "#C1CCD2"
    const val ROAD_CASING = "#252E34"
    const val RAILWAY = "#45515A"
    const val BOUNDARY = "#586770"
    const val LABEL = "#D6DDE1"
    const val LABEL_HALO = "#11161A"
}

internal fun darkFillColor(layerId: String): String? = when (layerId) {
    "water" -> CockpitDarkPalette.WATER
    "landcover_wood", "landuse_park" -> CockpitDarkPalette.GREEN
    "building" -> CockpitDarkPalette.BUILDING
    "landcover_ice_shelf", "landcover_glacier", "landuse_residential",
    "aeroway-area", "road_area_pier" -> CockpitDarkPalette.LAND
    else -> null
}

internal fun darkLineColor(layerId: String): String? = when {
    layerId == "waterway" -> CockpitDarkPalette.WATER
    layerId == "highway_path" -> CockpitDarkPalette.PATH
    layerId == "highway_minor" -> CockpitDarkPalette.MINOR_ROAD
    layerId.contains("highway_major") && layerId.contains("casing") ->
        CockpitDarkPalette.ROAD_CASING
    layerId.contains("highway_major") -> CockpitDarkPalette.MAJOR_ROAD
    layerId.contains("highway_motorway") && layerId.contains("casing") ->
        CockpitDarkPalette.ROAD_CASING
    layerId.contains("highway_motorway") -> CockpitDarkPalette.MOTORWAY
    layerId.startsWith("railway") -> CockpitDarkPalette.RAILWAY
    layerId.startsWith("boundary") -> CockpitDarkPalette.BOUNDARY
    else -> null
}

private fun applyCockpitDarkPalette(style: Style): Int {
    var changedLayers = 0
    style.layers.forEach { layer ->
        when (layer) {
            is BackgroundLayer -> {
                layer.setProperties(backgroundColor(CockpitDarkPalette.BACKGROUND))
                changedLayers++
            }
            is FillLayer -> darkFillColor(layer.id)?.let { color ->
                layer.setProperties(fillColor(color))
                changedLayers++
            }
            is LineLayer -> darkLineColor(layer.id)?.let { color ->
                layer.setProperties(lineColor(color))
                changedLayers++
            }
            is SymbolLayer -> {
                layer.setProperties(
                    textColor(CockpitDarkPalette.LABEL),
                    textHaloColor(CockpitDarkPalette.LABEL_HALO),
                    textHaloWidth(1.2f),
                )
                changedLayers++
            }
        }
    }
    return changedLayers
}

enum class MapCacheLimit(val displayName: String, val bytes: Long) {
    MB_512("512 MB", 512L * 1024L * 1024L),
    GB_1("1 GB", 1024L * 1024L * 1024L),
    GB_2("2 GB", 2L * 1024L * 1024L * 1024L),
    GB_5("5 GB", 5L * 1024L * 1024L * 1024L),
}

data class MapDiagnostics(
    val networkAvailable: Boolean = false,
    val gpsAvailable: Boolean = false,
    val hasKnownPosition: Boolean = false,
    val mapStatus: String = "",
    val mapError: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val debugLogPath: String = "",
    val poiSourceCount: Int = 0,
    val poiPointCount: Int = 0,
)

private data class MapPosition(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val speedMps: Float,
)

private fun MapPosition.toMotionSample(elapsedRealtimeMs: Long): MapMotionSample =
    MapMotionSample(latitude, longitude, bearing, speedMps, elapsedRealtimeMs)

internal fun MapMotionSample.differsVisuallyFrom(
    previous: MapMotionSample?,
    zoom: Double,
): Boolean {
    if (previous == null) return true
    if (kotlin.math.abs(shortestBearingDelta(previous.bearing, bearing)) >=
        CAMERA_MINIMUM_BEARING_CHANGE
    ) {
        return true
    }
    val latitudeMetres = Math.toRadians(latitude - previous.latitude) * 6_371_000.0
    var longitudeDelta = longitude - previous.longitude
    if (longitudeDelta > 180.0) longitudeDelta -= 360.0
    if (longitudeDelta < -180.0) longitudeDelta += 360.0
    val longitudeMetres = Math.toRadians(longitudeDelta) * 6_371_000.0 *
        cos(Math.toRadians((latitude + previous.latitude) / 2.0))
    val metresPerPixel = 156_543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)
    return hypot(latitudeMetres, longitudeMetres) >=
        metresPerPixel * CAMERA_MINIMUM_PIXEL_MOVEMENT
}

@Stable
internal class MapCameraTrackingState {
    var isFollowing by mutableStateOf(true)
        private set

    private var forceNextUpdate = true

    fun startExploration(): Boolean {
        if (!isFollowing) return false
        isFollowing = false
        return true
    }

    fun recenter(): Boolean {
        val changed = !isFollowing
        isFollowing = true
        forceNextUpdate = true
        return changed
    }

    fun shouldUpdate(
        frameDue: Boolean,
        gestureActive: Boolean,
        rendered: MapMotionSample,
        previous: MapMotionSample?,
        zoom: Double,
    ): Boolean = frameDue &&
        isFollowing &&
        !gestureActive &&
        (forceNextUpdate || rendered.differsVisuallyFrom(previous, zoom))

    fun markUpdated() {
        forceNextUpdate = false
    }
}

internal fun shouldStartMapExploration(pointerCount: Int): Boolean = pointerCount == 1

private class MapViewSession(val id: String) {
    val ready = CompletableDeferred<MapView>()
    val mapReady = CompletableDeferred<MapLibreMap>()
    var view: MapView? = null
    var map: MapLibreMap? = null
    var styleLoadJob: Job? = null
    var poiPulseJob: Job? = null
    var lifecycleActive = false
    var currentZoom = 16.0
    var userGestureActive = false
    var paddedWidth = 0
    var paddedHeight = 0
}

@Composable
internal fun CockpitMap(
    poiSnapshot: PoiSnapshot,
    poiRepository: PoiRepository,
    locationRepository: LocationRepository,
    modifier: Modifier = Modifier,
    tileStyle: MapTileStyle = MapTileStyle.POSITRON,
    colorMode: MapColorMode = MapColorMode.AUTOMATIC,
    vehicleLightsOn: Boolean? = null,
    cacheLimit: MapCacheLimit = MapCacheLimit.GB_2,
    cacheGeneration: Int = 0,
    debugEnabled: Boolean = false,
    onDiagnosticsChanged: (MapDiagnostics) -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemNight = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val effectiveTileStyle =
        resolveMapTileStyle(colorMode, vehicleLightsOn, systemNight, tileStyle)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val lifecycleActive = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val debugLogger = remember { MapDebugLogger.get(context) }
    val cockpitId = remember { "C-${UUID.randomUUID().toString().take(8)}" }
    val currentDebugEnabled by rememberUpdatedState(debugEnabled)
    val diagnostic: (String) -> Unit = { message ->
        if (currentDebugEnabled) debugLogger.write(message)
    }
    var permissionGranted by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    val locationState by locationRepository.state.collectAsStateWithLifecycle()
    var position by remember(locationRepository) {
        mutableStateOf(
            locationState.position?.let {
                MapPosition(it.latitude, it.longitude, it.bearing, it.speedMps)
            } ?: MapPosition(40.4168, -3.7038, 0f, 0f)
        )
    }
    val motionSmoother = remember {
        MapMotionSmoother().apply {
            add(position.toMotionSample(SystemClock.elapsedRealtime()))
        }
    }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val statusInitializing = stringResource(R.string.map_status_initializing)
    val statusLoading = stringResource(R.string.map_status_loading)
    val statusLoaded = stringResource(R.string.map_status_loaded)
    val statusError = stringResource(R.string.map_status_error)
    val statusInitializationError = stringResource(R.string.map_status_initialization_error)
    val noConnection = stringResource(R.string.map_offline).uppercase(configuration.locales[0])
    val noGpsSignal = stringResource(R.string.map_gps_no_signal).uppercase(configuration.locales[0])
    val noGps = stringResource(R.string.map_gps_no_position).uppercase(configuration.locales[0])
    var mapStatus by remember { mutableStateOf(statusInitializing) }
    var mapError by remember { mutableStateOf("") }
    var debugLogPath by remember { mutableStateOf("") }
    var rendererStarted by remember { mutableStateOf(false) }
    val cameraTracking = remember { MapCameraTrackingState() }
    val networkAvailable = rememberNetworkAvailability(context, lifecycleActive)

    LaunchedEffect(locationState.position) {
        locationState.position?.let {
            val acceptedPosition = MapPosition(it.latitude, it.longitude, it.bearing, it.speedMps)
            position = acceptedPosition
            motionSmoother.add(
                acceptedPosition.toMotionSample(
                    it.acceptedElapsedMillis ?: SystemClock.elapsedRealtime()
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        // MapView and its SQL cache must never compete with the first frames of
        // the HOME activity. Render the complete cockpit first, then attach the
        // independently loading map.
        withFrameNanos { }
        withFrameNanos { }
        delay(250)
        rendererStarted = true
        diagnostic("$cockpitId MAPA DIFERIDO | renderer autorizado tras primeros frames")
    }

    DisposableEffect(cockpitId) {
        diagnostic("$cockpitId COMPOSICIÓN ENTER")
        onDispose {
            diagnostic("$cockpitId COMPOSICIÓN EXIT")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasLocationPermission(context)
    }

    LaunchedEffect(lifecycleActive) {
        if (!lifecycleActive) return@LaunchedEffect
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    DisposableEffect(permissionGranted, lifecycleActive, locationRepository) {
        if (!permissionGranted || !lifecycleActive) {
            return@DisposableEffect onDispose {}
        }
        val stop = locationRepository.start(diagnostic)
        onDispose(stop)
    }

    val hasKnownPosition = locationState.position != null
    val gpsAvailable = permissionGranted && locationState.gpsAvailable(tick)

    LaunchedEffect(debugEnabled, effectiveTileStyle) {
        debugLogger.setEnabled(debugEnabled)
        debugLogPath = debugLogger.displayPath
        if (debugEnabled) {
            debugLogger.write(
                "ESTILO VECTORIAL | OpenFreeMap ${effectiveTileStyle.displayName} / OpenStreetMap"
            )
            debugLogger.write(networkDiagnostics(context))
            debugLogger.write(cacheDiagnostics(context, cacheLimit))
            runStyleHttpProbe(effectiveTileStyle) {
                debugLogger.write(it)
            }
        }
    }

    LaunchedEffect(debugEnabled, mapStatus, mapError) {
        if (debugEnabled) {
            debugLogger.write(
                "MAPA | estado=$mapStatus" +
                    mapError.takeIf { it.isNotBlank() }?.let { " | error=$it" }.orEmpty()
            )
        }
    }

    LaunchedEffect(debugEnabled, networkAvailable) {
        if (debugEnabled) {
            debugLogger.write("RED | ${if (networkAvailable) "CONECTADA" else "SIN CONEXIÓN"}")
        }
    }

    LaunchedEffect(debugEnabled, gpsAvailable, hasKnownPosition) {
        if (debugEnabled) {
            debugLogger.write(
                "GPS | señal=$gpsAvailable | última_posición=$hasKnownPosition"
            )
        }
    }

    LaunchedEffect(
        networkAvailable,
        gpsAvailable,
        hasKnownPosition,
        mapStatus,
        mapError,
        position,
        debugLogPath,
        poiSnapshot,
    ) {
        onDiagnosticsChanged(
            MapDiagnostics(
                networkAvailable = networkAvailable,
                gpsAvailable = gpsAvailable,
                hasKnownPosition = hasKnownPosition,
                mapStatus = mapStatus,
                mapError = mapError,
                latitude = position.latitude.takeIf { hasKnownPosition },
                longitude = position.longitude.takeIf { hasKnownPosition },
                debugLogPath = debugLogPath,
                poiSourceCount = poiSnapshot.sources.size,
                poiPointCount = poiSnapshot.totalPoints,
            )
        )
    }

    Box(
        modifier
            .clipToBounds()
            .background(Color(0xFF071014))
    ) {
        if (rendererStarted) {
            CockpitMapView(
                position = position,
                motionSmoother = motionSmoother,
                tileStyle = effectiveTileStyle,
                zoomPreferenceKey = tileStyle.cacheKey,
                cacheLimit = cacheLimit,
                cacheGeneration = cacheGeneration,
                debugEnabled = debugEnabled,
                lifecycleActive = lifecycleActive,
                networkAvailable = networkAvailable,
                markerColor = when {
                    !networkAvailable -> Color(0xFFFF3B30)
                    !gpsAvailable -> Color(0xFFFFC107)
                    else -> Color(0xFF00E5FF)
                },
                poiSnapshot = poiSnapshot,
                poiRepository = poiRepository,
                statusLoading = statusLoading,
                statusLoaded = statusLoaded,
                statusError = statusError,
                statusInitializationError = statusInitializationError,
                onStatusChanged = { status, error ->
                    mapStatus = status
                    mapError = error
                },
                onDiagnostic = diagnostic,
                cameraTracking = cameraTracking,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "© OpenStreetMap · © OpenMapTiles",
            color = Color.White.copy(alpha = .42f),
            fontSize = 7.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!networkAvailable) StatusBadge(noConnection)
            if (!gpsAvailable) StatusBadge(if (hasKnownPosition) noGpsSignal else noGps)
            if (debugEnabled) StatusBadge(mapStatus)
        }

        if (rendererStarted && !cameraTracking.isFollowing) {
            RecenterMapButton(
                onClick = {
                    if (cameraTracking.recenter()) {
                        diagnostic("CÁMARA | seguimiento restaurado")
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun CockpitMapView(
    position: MapPosition,
    motionSmoother: MapMotionSmoother,
    tileStyle: MapTileStyle,
    zoomPreferenceKey: String,
    cacheLimit: MapCacheLimit,
    cacheGeneration: Int,
    debugEnabled: Boolean,
    lifecycleActive: Boolean,
    networkAvailable: Boolean,
    markerColor: Color,
    poiSnapshot: PoiSnapshot,
    poiRepository: PoiRepository,
    statusLoading: String,
    statusLoaded: String,
    statusError: String,
    statusInitializationError: String,
    onStatusChanged: (String, String) -> Unit,
    onDiagnostic: (String) -> Unit,
    cameraTracking: MapCameraTrackingState,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
    }
    val styleCache = remember {
        MapStyleCache(File(context.filesDir, "map-styles"), ::downloadMapStyleJson)
    }
    val initialZoom = remember(zoomPreferenceKey, tileStyle) {
        preferences.getFloat("zoom_$zoomPreferenceKey", MAP_DEFAULT_ZOOM.toFloat())
            .toDouble()
            .let(::clampMapZoom)
    }
    val session = remember(cacheGeneration, tileStyle, zoomPreferenceKey) {
        MapViewSession("M-${UUID.randomUUID().toString().take(8)}")
    }
    LaunchedEffect(session) {
        cameraTracking.recenter()
    }
    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF071014)),
    ) {
        key(cacheGeneration, tileStyle, zoomPreferenceKey) {
            AndroidView(
                factory = { viewContext ->
                    FrameLayout(viewContext).apply host@{
                        setBackgroundColor(android.graphics.Color.rgb(7, 16, 20))
                        val created = runCatching {
                            MapLibre.getInstance(viewContext.applicationContext)
                            onDiagnostic(
                                "${session.id} MAPLIBRE CREANDO | estilo=${tileStyle.styleUrl}" +
                                    " | zoom=$initialZoom"
                            )
                            val mapOptions = MapLibreMapOptions
                                .createFromAttributes(viewContext)
                                .textureMode(true)
                            MapView(viewContext, mapOptions).apply mapView@{
                                setBackgroundColor(android.graphics.Color.rgb(7, 16, 20))
                                isForceDarkAllowed = false
                                onCreate(Bundle())
                                onDiagnostic(
                                    "${session.id} RENDERER | tipo=" +
                                        "${getRenderView().javaClass.simpleName}" +
                                        " | pixelRatio=$pixelRatio" +
                                        " | density=${resources.displayMetrics.density}"
                                )
                                addOnDidFailLoadingMapListener { error ->
                                    onDiagnostic("${session.id} MAPLIBRE ERROR | $error")
                                    onStatusChanged(statusError, error)
                                }
                                addOnDidFinishRenderingMapListener { fully ->
                                    if (fully) {
                                        onDiagnostic("${session.id} MAPA RENDERIZADO COMPLETO")
                                        onStatusChanged(statusLoaded, "")
                                    }
                                }
                                getMapAsync { map ->
                                    session.map = map
                                    session.mapReady.complete(map)
                                    map.setMinZoomPreference(MAP_MINIMUM_ZOOM)
                                    map.setMaxZoomPreference(MAP_MAXIMUM_ZOOM)
                                    map.uiSettings.apply {
                                        isCompassEnabled = false
                                        isLogoEnabled = false
                                        isAttributionEnabled = false
                                        isRotateGesturesEnabled = false
                                        isTiltGesturesEnabled = false
                                        isScrollGesturesEnabled = true
                                        isZoomGesturesEnabled = true
                                        isDoubleTapGesturesEnabled = true
                                        isQuickZoomGesturesEnabled = true
                                    }
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(position.latitude, position.longitude))
                                        .zoom(initialZoom)
                                        .bearing(position.bearing.toDouble())
                                        .tilt(38.0)
                                        .build()
                                    session.currentZoom = initialZoom
                                    map.addOnMoveListener(
                                        object : MapLibreMap.OnMoveListener {
                                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                                if (
                                                    shouldStartMapExploration(detector.pointersCount) &&
                                                    cameraTracking.startExploration()
                                                ) {
                                                    onDiagnostic(
                                                        "${session.id} CÁMARA | exploración manual"
                                                    )
                                                }
                                            }

                                            override fun onMove(detector: MoveGestureDetector) = Unit

                                            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
                                        }
                                    )
                                    map.addOnCameraMoveStartedListener { reason ->
                                        if (
                                            reason ==
                                            MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                                        ) {
                                            session.userGestureActive = true
                                        }
                                    }
                                    map.addOnCameraIdleListener {
                                        if (session.userGestureActive) {
                                            session.userGestureActive = false
                                            val zoom = clampMapZoom(map.cameraPosition.zoom)
                                            session.currentZoom = zoom
                                            preferences.edit {
                                                putFloat("zoom_$zoomPreferenceKey", zoom.toFloat())
                                            }
                                        }
                                    }
                                    configureVectorCache(
                                        viewContext,
                                        cacheLimit,
                                        onDiagnostic,
                                    ) {
                                        session.styleLoadJob?.cancel()
                                        session.styleLoadJob = coroutineScope.launch {
                                            val resolution = withContext(Dispatchers.IO) {
                                                styleCache.resolve(
                                                    tileStyle.cacheKey,
                                                    tileStyle.styleUrl,
                                                    networkAvailable,
                                                )
                                            }
                                            if (session.map !== map) return@launch
                                            resolution.error?.let {
                                                onDiagnostic(
                                                    "${session.id} ESTILO AVISO | $it"
                                                )
                                            }
                                            val onStyleLoaded: (Style) -> Unit = { style ->
                                                if (tileStyle == MapTileStyle.DARK) {
                                                    val changedLayers = applyCockpitDarkPalette(style)
                                                    onDiagnostic(
                                                        "${session.id} PALETA NOCTURNA | " +
                                                        "capas adaptadas=$changedLayers"
                                                    )
                                                }
                                                installPoiLayers(
                                                    viewContext,
                                                    style,
                                                    poiSnapshot,
                                                    poiRepository,
                                                    session,
                                                    coroutineScope,
                                                    onDiagnostic,
                                                )
                                                onDiagnostic(
                                                    "${session.id} ESTILO CARGADO | " +
                                                        "origen=${resolution.origin}"
                                                )
                                                onStatusChanged(statusLoading, "")
                                            }
                                            if (resolution.json != null) {
                                                map.setStyle(
                                                    Style.Builder().fromJson(resolution.json),
                                                    onStyleLoaded,
                                                )
                                            } else {
                                                onDiagnostic(
                                                    "${session.id} ESTILO REMOTO DE EMERGENCIA | " +
                                                        tileStyle.styleUrl
                                                )
                                                map.setStyle(tileStyle.styleUrl, onStyleLoaded)
                                            }
                                        }
                                    }
                                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                        updateMapLibrePadding(session, map, width, height)
                                    }
                                    if (width > 0 && height > 0) {
                                        updateMapLibrePadding(session, map, width, height)
                                    }
                                    onDiagnostic(
                                        "${session.id} MAPLIBRE LISTO | tamaño=${width}x${height}" +
                                            " | centro=${position.latitude},${position.longitude}"
                                    )
                                }
                            }
                        }.onFailure { error ->
                            onDiagnostic("MAPLIBRE CREACIÓN ERROR | ${error.diagnosticDescription()}")
                            onStatusChanged(
                                statusError,
                                error.message ?: error.javaClass.simpleName,
                            )
                        }.getOrNull()
                        if (created != null) {
                            addView(
                                created,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            session.view = created
                            session.ready.complete(created)
                            onStatusChanged(statusLoading, "")
                        }
                    }
                },
                update = { host ->
                    val view = host.getChildAt(0) as? MapView ?: return@AndroidView
                    session.map?.let {
                        updateMapLibrePadding(session, it, view.width, view.height)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        LaunchedEffect(session, motionSmoother, lifecycleActive) {
            if (!lifecycleActive) return@LaunchedEffect
            val map = withTimeoutOrNull(8_000L) {
                session.mapReady.await()
            }
            if (map == null) {
                onDiagnostic("${session.id} ERROR | getMapAsync agotó 8 segundos")
                onStatusChanged(statusInitializationError, "MapLibre timeout")
                return@LaunchedEffect
            }
            var lastRendered: MapMotionSample? = null
            var lastCameraFrameNanos = 0L
            onDiagnostic("${session.id} CÁMARA CONTINUA | retardo=250ms | predicción_máxima=1000ms")
            while (currentCoroutineContext().isActive) {
                withFrameNanos { frameTimeNanos ->
                    val rendered = motionSmoother.positionAt(SystemClock.elapsedRealtime())
                    val frameDue = frameTimeNanos - lastCameraFrameNanos >=
                        CAMERA_FRAME_INTERVAL_NANOS
                    if (rendered != null && cameraTracking.shouldUpdate(
                            frameDue = frameDue,
                            gestureActive = session.userGestureActive,
                            rendered = rendered,
                            previous = lastRendered,
                            zoom = session.currentZoom,
                        )
                    ) {
                        map.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(rendered.latitude, rendered.longitude))
                                    .zoom(session.currentZoom)
                                    .bearing(rendered.bearing.toDouble())
                                    .tilt(38.0)
                                    .build()
                            )
                        )
                        lastRendered = rendered
                        lastCameraFrameNanos = frameTimeNanos
                        cameraTracking.markUpdated()
                    }
                }
            }
        }

        LaunchedEffect(session, poiSnapshot, lifecycleActive) {
            if (!lifecycleActive) {
                session.poiPulseJob?.cancel()
                session.poiPulseJob = null
                return@LaunchedEffect
            }
            val map = withTimeoutOrNull(8_000L) { session.mapReady.await() }
                ?: return@LaunchedEffect
            map.getStyle { style ->
                val existing = style.getSourceAs<GeoJsonSource>(POI_SOURCE_ID)
                if (existing != null) {
                    existing.setGeoJson(poiSnapshot.geoJson)
                    installPoiLayers(
                        context,
                        style,
                        poiSnapshot,
                        poiRepository,
                        session,
                        coroutineScope,
                        onDiagnostic,
                    )
                }
            }
        }

        DisposableEffect(session, lifecycleActive) {
            session.lifecycleActive = lifecycleActive
            if (!lifecycleActive) {
                session.userGestureActive = false
                session.poiPulseJob?.cancel()
                session.poiPulseJob = null
            }
            val view = session.view
            if (view != null) {
                if (lifecycleActive) {
                    view.onStart()
                    view.onResume()
                    onDiagnostic(
                        "${session.id} MAPLIBRE RESUME | visible=${view.isShown}" +
                            " | adjunto=${view.isAttachedToWindow}"
                    )
                } else {
                    view.onPause()
                    view.onStop()
                    onDiagnostic("${session.id} MAPLIBRE PAUSE | actividad no visible")
                }
            }
            onDispose {
                session.userGestureActive = false
            }
        }

        DisposableEffect(session) {
            onDispose {
                val view = session.view
                if (view != null) {
                    onDiagnostic(
                        "${session.id} MAPLIBRE DESTROY | pausa y liberación GL"
                    )
                    session.styleLoadJob?.cancel()
                    session.styleLoadJob = null
                    session.poiPulseJob?.cancel()
                    session.poiPulseJob = null
                    view.onPause()
                    view.onStop()
                    view.onDestroy()
                    session.map = null
                    session.view = null
                }
            }
        }

        if (cameraTracking.isFollowing) {
            MapVehicleMarker(
                color = markerColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = maxHeight * .30f)
                    .size(38.dp),
            )
        }
    }
}

private fun installPoiLayers(
    context: Context,
    style: Style,
    snapshot: PoiSnapshot,
    repository: PoiRepository,
    session: MapViewSession,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    diagnostic: (String) -> Unit,
) {
    if (style.getSource(POI_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(POI_SOURCE_ID, snapshot.geoJson))
    }
    snapshot.icons.filter { it.code in snapshot.referencedIconCodes }.forEach { icon ->
        repository.loadIconBitmap(icon)?.let { style.addImage(icon.code, it) }
    }
    if (style.getLayer(POI_PULSE_FIRST_LAYER_ID) == null) {
        style.addImage(POI_PULSE_ICON_ID, createPoiPulseBitmap(), true)
        style.addLayer(
            SymbolLayer(POI_PULSE_FIRST_LAYER_ID, POI_SOURCE_ID).withFilter(
                Expression.eq(Expression.get("pulseEnabled"), true),
            ).withProperties(
                iconImage(POI_PULSE_ICON_ID),
                iconColor(Expression.get("pulseColor")),
                iconSize(poiPulseIconSize(0f)),
                iconOpacity(.82f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            )
        )
        style.addLayer(
            SymbolLayer(POI_PULSE_SECOND_LAYER_ID, POI_SOURCE_ID).withFilter(
                Expression.eq(Expression.get("pulseEnabled"), true),
            ).withProperties(
                iconImage(POI_PULSE_ICON_ID),
                iconColor(Expression.get("pulseColor")),
                iconSize(poiPulseIconSize(.5f)),
                iconOpacity(.41f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            )
        )
    }
    if (style.getLayer(POI_ICON_LAYER_ID) == null) {
        val icon: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_poi)
            ?: return
        style.addImage(POI_DEFAULT_ICON_ID, icon)
        style.addLayer(
            SymbolLayer(POI_ICON_LAYER_ID, POI_SOURCE_ID).withProperties(
                iconImage(Expression.get("resolvedIcon")),
                iconSize(.78f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            )
        )
    }
    session.poiPulseJob?.cancel()
    if (!snapshot.hasPulsingPoints || !session.lifecycleActive || session.map == null) return
    session.poiPulseJob = coroutineScope.launch {
        val easing = PathInterpolator(.18f, .66f, .22f, 1f)
        while (true) {
            val phase = (SystemClock.elapsedRealtime() % POI_PULSE_DURATION_MS).toFloat() /
                POI_PULSE_DURATION_MS
            val first = style.getLayerAs<SymbolLayer>(POI_PULSE_FIRST_LAYER_ID) ?: break
            val second = style.getLayerAs<SymbolLayer>(POI_PULSE_SECOND_LAYER_ID) ?: break
            updatePoiPulse(first, easing.getInterpolation(phase))
            updatePoiPulse(second, easing.getInterpolation((phase + .5f) % 1f))
            delay(33)
        }
    }
    diagnostic("${session.id} POI CAPA | fuentes=${snapshot.sources.size} | puntos=${snapshot.totalPoints}")
}

private fun updatePoiPulse(layer: SymbolLayer, progress: Float) {
    layer.setProperties(
        iconSize(poiPulseIconSize(progress)),
        iconOpacity(.82f * (1f - progress)),
    )
}

internal fun poiPulseIconSize(progress: Float): Float =
    POI_PULSE_BASE_SIZE * (.96f + .62f * progress.coerceIn(0f, 1f))

private fun createPoiPulseBitmap(): Bitmap = Bitmap.createBitmap(
    64,
    64,
    Bitmap.Config.ARGB_8888,
).also { bitmap ->
    Canvas(bitmap).drawCircle(
        32f,
        32f,
        28f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        },
    )
}

private fun updateMapLibrePadding(
    session: MapViewSession,
    map: MapLibreMap,
    width: Int,
    height: Int,
) {
    if (width <= 0 || height <= 0) return
    if (session.paddedWidth == width && session.paddedHeight == height) return
    session.paddedWidth = width
    session.paddedHeight = height
    val camera = CameraPosition.Builder(map.cameraPosition)
        .padding(0.0, (height * .60f).toDouble(), 0.0, 0.0)
        .build()
    map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
}

private fun configureVectorCache(
    context: Context,
    cacheLimit: MapCacheLimit,
    diagnostic: (String) -> Unit,
    onReady: () -> Unit,
) {
    OfflineManager.getInstance(context.applicationContext)
        .setMaximumAmbientCacheSize(
            cacheLimit.bytes,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() {
                    diagnostic("CACHE MAPLIBRE | límite=${cacheLimit.bytes} | aplicado")
                    onReady()
                }

                override fun onError(message: String) {
                    diagnostic("CACHE MAPLIBRE ERROR | $message")
                    onReady()
                }
            },
        )
}

private fun networkDiagnostics(context: Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity.activeNetwork
        ?: return "RED DETALLE | sin red activa"
    val capabilities = connectivity.getNetworkCapabilities(network)
        ?: return "RED DETALLE | sin capabilities"
    val transports = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("MOVIL")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
    }
    return "RED DETALLE | transportes=${transports.joinToString().ifBlank { "OTRO" }}" +
        " | internet=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}" +
        " | validada=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}" +
        " | sin_medición=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}" +
        " | down=${capabilities.linkDownstreamBandwidthKbps}kbps" +
        " | up=${capabilities.linkUpstreamBandwidthKbps}kbps"
}

@SuppressLint("UsableSpace")
private fun cacheDiagnostics(context: Context, limit: MapCacheLimit): String {
    val roots = listOf(context.cacheDir, context.filesDir)
    val existingFiles = roots
        .flatMap { root -> root.walkTopDown().filter(File::isFile).toList() }
        .filter { file ->
            file.path.contains("maplibre", ignoreCase = true) ||
                file.name.contains("mbgl", ignoreCase = true) ||
                file.name.endsWith(".db")
        }
    val existingBytes = existingFiles.sumOf(File::length)
    val probe = File(context.cacheDir, ".maplibre-diagnostic-write-test")
    val writeResult = runCatching {
        probe.writeText("A5")
        probe.readText() == "A5"
    }
    probe.delete()
    return "CACHE VECTORIAL | cache=${context.cacheDir.absolutePath}" +
        " | escribible=${context.cacheDir.canWrite()}" +
        " | prueba_escritura=${writeResult.getOrDefault(false)}" +
        " | ficheros=${existingFiles.size} | bytes=$existingBytes" +
        " | libre=${context.cacheDir.usableSpace} | límite=${limit.bytes}" +
        writeResult.exceptionOrNull()?.let { " | error=${it.diagnosticDescription()}" }.orEmpty()
}

private suspend fun runStyleHttpProbe(
    style: MapTileStyle,
    diagnostic: (String) -> Unit,
) = withContext(Dispatchers.IO) {
    val url = URL(style.styleUrl)
    val started = SystemClock.elapsedRealtime()
    diagnostic("CONTROL ESTILO INICIO | url=$url")
    runCatching {
        val dnsStarted = SystemClock.elapsedRealtime()
        val addresses = InetAddress.getAllByName(url.host)
        diagnostic(
            "CONTROL DNS OK | host=${url.host}" +
                " | direcciones=${addresses.joinToString { it.hostAddress.orEmpty() }}" +
                " | ms=${SystemClock.elapsedRealtime() - dnsStarted}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "com.lito.a5launcher diagnostic")
        }
        try {
            val responseStarted = SystemClock.elapsedRealtime()
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val bytes = if (code in 200..299) connection.inputStream.use { it.readBytes() }
            else connection.errorStream?.use { it.readBytes() } ?: byteArrayOf()
            val signature = bytes.take(8).joinToString("") { "%02x".format(it) }
            val tls = (connection as? HttpsURLConnection)?.let {
                " | tls=${it.cipherSuite}"
            }.orEmpty()
            diagnostic(
                "CONTROL ESTILO FIN | código=$code | tipo=$contentType" +
                    " | bytes=${bytes.size} | firma=$signature" +
                    " | respuesta_ms=${SystemClock.elapsedRealtime() - responseStarted}" +
                    " | total_ms=${SystemClock.elapsedRealtime() - started}$tls"
            )
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        diagnostic(
            "CONTROL ESTILO ERROR | ms=${SystemClock.elapsedRealtime() - started}" +
                " | ${it.diagnosticDescription()}"
        )
    }
}

private fun downloadMapStyleJson(styleUrl: String): String {
    val connection = (URL(styleUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("User-Agent", "com.lito.a5launcher map-style-cache")
    }
    return try {
        val code = connection.responseCode
        require(code in 200..299) { "Map style HTTP $code" }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun Throwable.diagnosticDescription(): String =
    generateSequence(this) { it.cause }
        .take(6)
        .joinToString(" <- ") { cause ->
            "${cause.javaClass.name}: ${cause.message.orEmpty()}"
        }

internal fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

internal fun shortestBearingDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

@Composable
private fun RecenterMapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.map_recenter)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xEE071014))
            .border(1.dp, Color.White.copy(alpha = .34f), RoundedCornerShape(12.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(25.dp)) {
            val stroke = 1.8.dp.toPx()
            val cyan = Color(0xFF00C8E0)
            drawCircle(
                color = Color.White.copy(alpha = .92f),
                radius = size.minDimension * .31f,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = cyan,
                radius = size.minDimension * .09f,
            )
            val centre = Offset(size.width / 2f, size.height / 2f)
            val inner = size.minDimension * .38f
            val outer = size.minDimension * .49f
            listOf(
                Offset(0f, -1f),
                Offset(1f, 0f),
                Offset(0f, 1f),
                Offset(-1f, 0f),
            ).forEach { direction ->
                drawLine(
                    color = Color.White.copy(alpha = .92f),
                    start = centre + direction * inner,
                    end = centre + direction * outer,
                    strokeWidth = stroke,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC261E13))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = Color(0xFFFF9D32),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            color = Color(0xFFFFC16F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MapVehicleMarker(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val markerResource = when (color) {
        Color(0xFFFF3B30) -> R.drawable.map_position_waze_no_network
        Color(0xFFFFC107) -> R.drawable.map_position_waze_no_gps
        else -> R.drawable.map_position_waze
    }
    Image(
        painter = painterResource(markerResource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun rememberNetworkAvailability(context: Context, active: Boolean): Boolean {
    val connectivity = remember {
        context.getSystemService(ConnectivityManager::class.java)
    }
    fun currentStatus(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    var available by remember { mutableStateOf(currentStatus()) }
    DisposableEffect(connectivity, active) {
        if (!active) return@DisposableEffect onDispose {}
        available = currentStatus()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = currentStatus()
            }

            override fun onLost(network: Network) {
                available = currentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                available = currentStatus()
            }
        }
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    return available
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

private suspend fun clearMapLibreAmbientCache(context: Context) {
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            MapLibre.getInstance(context.applicationContext)
            OfflineManager.getInstance(context.applicationContext)
                .clearAmbientCache(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(message: String) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
        }
    }
}

suspend fun clearVectorMapCache(context: Context): Int {
    clearMapLibreAmbientCache(context)
    val targets = vectorMapRemovableTargets(context)
    return withContext(Dispatchers.IO) {
        val files = targets.sumOf { target ->
            target.walkTopDown().count { it.isFile }
        }
        targets.forEach(File::deleteRecursively)
        files
    }
}
