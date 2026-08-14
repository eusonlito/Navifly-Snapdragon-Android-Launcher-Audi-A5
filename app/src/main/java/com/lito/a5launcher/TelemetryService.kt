package com.lito.a5launcher

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.database.ContentObserver
import android.database.Cursor
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.lito.a5launcher.model.DoorStatus
import com.szchoiceway.eventcenter.ICallbackfn
import com.szchoiceway.eventcenter.IEventService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.longOrNull(key: String): Long? = (opt(key) as? Number)?.toLong()

private const val PRODUCTION_TRIP_SCHEMA = 2
private const val CURRENT_TRIP_SCHEMA = 3

internal fun isCompatibleTripSchema(schema: Int): Boolean =
    schema in PRODUCTION_TRIP_SCHEMA..CURRENT_TRIP_SCHEMA

private fun SharedPreferences.getNonNegativeDoubleBits(key: String, fallback: Double): Double =
    Double.fromBits(getLong(key, fallback.toRawBits()))
        .takeIf { it.isFinite() && it >= 0.0 } ?: fallback

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryService : Service() {

    companion object {
        private const val TAG = "TelemetryService"
        private const val PROVIDER_URI = "content://com.szchoiceway.eventcenter.SysVarProvider/SysVar"
        private const val KESAIWEI_RECORD_PARK = "KESAIWEI_RECORD_PARK"
        private const val KESAIWEI_RECORD_BELT = "KESAIWEI_RECORD_BELT"
        private const val KSW_DATA_SMALL_LIGHT_ON = "KSW_DATA_SMALL_LIGHT_ON"
        private const val REPLAY_ASSET = "telemetry-replay.jsonl"
        private const val REPLAY_LOOP_PAUSE_MS = 2_000L
        private const val TELEMETRY_CHANNEL_ID = "vehicle_telemetry"
        private const val TELEMETRY_NOTIFICATION_ID = 2001
        private const val TRIP_PREFS = "current_boot_trip"
        private const val TRIP_SCHEMA = "schema"
        private const val TRIP_BOOT_COUNT = "boot_count"
        private const val TRIP_STARTED_AT = "started_at_elapsed_ms"
        private const val TRIP_DISTANCE_BITS = "distance_bits"
        private const val TRIP_FUEL_USED_BITS = "fuel_used_bits"
        private const val TRIP_VIRTUAL_FUEL_BITS = "virtual_fuel_bits"
        private const val TRIP_CALIBRATION_FACTOR_BITS = "calibration_factor_bits"
        private const val TRIP_LAST_FUEL_LITRES = "last_fuel_litres"
        private const val TRIP_CALIBRATION_ANCHOR_FUEL_LITRES = "calibration_anchor_fuel_litres"
        private const val TRIP_UNCALIBRATED_FUEL_BITS = "uncalibrated_fuel_bits"
        private const val TRIP_RECENT_CONSUMPTION = "recent_consumption"
        private const val TRIP_RANGE_BASELINE_BITS = "range_baseline_bits"
        private const val RANGE_PREFS = "range_consumption_model"
        private const val RANGE_SCHEMA = "schema"
        private const val CURRENT_RANGE_SCHEMA = 1
        private const val RANGE_LEARNED_CONSUMPTION_BITS = "learned_consumption_bits"
        private const val RANGE_PENDING_DISTANCE_BITS = "pending_distance_bits"
        private const val RANGE_PENDING_FUEL_BITS = "pending_fuel_bits"
        private const val REFUEL_PREFS = "distance_since_refuel"
        private const val REFUEL_DISTANCE_BITS = "distance_bits"
        private const val REFUEL_LAST_FUEL_LITRES = "last_fuel_litres"
    }

    private val binder = LocalBinder()
    // EventCenter sends related fields as an ordered stream. A single worker
    // preserves callback order while SupervisorJob prevents one malformed event
    // from cancelling telemetry for the remainder of the process.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
    )

    // Granular StateFlows to minimize recomposition in Compose (60 fps requirement)
    private val _speedFlow = MutableStateFlow(0)
    val speedFlow: StateFlow<Int> = _speedFlow.asStateFlow()

    private val _rpmFlow = MutableStateFlow(0)
    val rpmFlow: StateFlow<Int> = _rpmFlow.asStateFlow()

    private val _doorStatusFlow = MutableStateFlow(DoorStatus())
    val doorStatusFlow: StateFlow<DoorStatus> = _doorStatusFlow.asStateFlow()

    private val _fuelFlow = MutableStateFlow(0)
    val fuelFlow: StateFlow<Int> = _fuelFlow.asStateFlow()

    private val _mileageFlow = MutableStateFlow(0)
    val mileageFlow: StateFlow<Int> = _mileageFlow.asStateFlow()

    private val _rangeFlow = MutableStateFlow(0) // Unknown until validated CAN data arrives
    val rangeFlow: StateFlow<Int> = _rangeFlow.asStateFlow()

    private val _outsideTempFlow = MutableStateFlow(Double.NaN)
    val outsideTempFlow: StateFlow<Double> = _outsideTempFlow.asStateFlow()

    private val _seatbeltFlow = MutableStateFlow(true)
    val seatbeltFlow: StateFlow<Boolean> = _seatbeltFlow.asStateFlow()

    private val _parkingBrakeFlow = MutableStateFlow(false)
    val parkingBrakeFlow: StateFlow<Boolean> = _parkingBrakeFlow.asStateFlow()

    private val _lightsOnFlow = MutableStateFlow<Boolean?>(null)
    val lightsOnFlow: StateFlow<Boolean?> = _lightsOnFlow.asStateFlow()

    private val _gearFlow = MutableStateFlow(0)
    val gearFlow: StateFlow<Int> = _gearFlow.asStateFlow()

    private val _tripElapsedRealtimeMsFlow = MutableStateFlow(0L)
    val tripElapsedRealtimeMsFlow: StateFlow<Long> = _tripElapsedRealtimeMsFlow.asStateFlow()

    private val _tripDistanceKmFlow = MutableStateFlow(0.0)
    val tripDistanceKmFlow: StateFlow<Double> = _tripDistanceKmFlow.asStateFlow()

    private val _distanceSinceRefuelKmFlow = MutableStateFlow(0.0)
    val distanceSinceRefuelKmFlow: StateFlow<Double> = _distanceSinceRefuelKmFlow.asStateFlow()

    private val _averageConsumptionFlow = MutableStateFlow(0.0)
    val averageConsumptionFlow: StateFlow<Double> = _averageConsumptionFlow.asStateFlow()

    // One atomic sample per message 90. SharedFlow deliberately emits repeated
    // equal frames because the gear estimator's hysteresis counts CAN samples.
    private val _drivingSampleFlow = MutableSharedFlow<DrivingSample>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val drivingSampleFlow: SharedFlow<DrivingSample> = _drivingSampleFlow.asSharedFlow()

    // Remote IPC Service
    private var mEvtService: IEventService? = null
    private var isBound = false
    private var eventConnectionJob: Job? = null
    private val reconnectPolicy = EventServiceReconnectPolicy()
    private var replayJob: Job? = null
    private var tripMetricsJob: Job? = null
    private lateinit var tripSession: TripSessionTracker
    private lateinit var distanceSinceRefuelTracker: DistanceSinceRefuelTracker
    private val tripPreferences by lazy {
        getSharedPreferences(TRIP_PREFS, Context.MODE_PRIVATE)
    }
    private val refuelPreferences by lazy {
        getSharedPreferences(REFUEL_PREFS, Context.MODE_PRIVATE)
    }
    private val rangePreferences by lazy {
        getSharedPreferences(RANGE_PREFS, Context.MODE_PRIVATE)
    }
    private var currentBootCount = -1
    private var lastPersistedTripVersion = 0L
    private var lastPersistedRangeState: RangeConsumptionState? = null
    @Volatile private var shuttingDown = false
    @Volatile private var replayActive = false
    private val providerObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { updateVitalsFromProvider() }
        }
    }

    private val mSerCon = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Connected to EventService IPC!")
            mEvtService = IEventService.Stub.asInterface(service)
            try {
                requireNotNull(mEvtService) { "EventService returned a null binder interface" }
                    .setDashBoardCallback(mDashBoardCallback)
                reconnectPolicy.onConnected()
                eventConnectionJob?.cancel()
                eventConnectionJob = null
                stopTelemetryReplay()
                Log.i(TAG, "DashBoardCallback successfully registered.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set dashboard callback: ${e.message}", e)
                handleEventServiceFailure(EventServiceFailure.CALLBACK_REGISTRATION_FAILED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "EventService disconnected.")
            handleEventServiceFailure(EventServiceFailure.DISCONNECTED)
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            onServiceDisconnected(name)
        }
    }

    private val mDashBoardCallback = object : ICallbackfn.Stub() {
        override fun notifyEvt(msg_what: Int, arg1: Int, arg2: Int, bArr: ByteArray?, str: String?) {
            // Process decoding strictly on Dispatchers.Default (Background) to maintain 60 FPS
            scope.launch {
                when (msg_what) {
                    90 -> { // Core Telemetry
                        bArr?.let(TelemetryDecoder::decodeCore)?.let { telemetry ->
                            val rawGearType = try {
                                mEvtService?.getGearType() ?: _gearFlow.value
                            } catch (e: Exception) {
                                Log.e(TAG, "Error calling getGearType(): ${e.message}")
                                _gearFlow.value
                            }

                            _gearFlow.value = rawGearType
                            _speedFlow.value = telemetry.speed
                            _rpmFlow.value = telemetry.rpm
                            _drivingSampleFlow.tryEmit(
                                DrivingSample(
                                    speed = telemetry.speed,
                                    rpm = telemetry.rpm,
                                    rawGearType = rawGearType,
                                )
                            )
                            val now = SystemClock.elapsedRealtime()
                            publishTripMetrics(
                                tripSession.onTelemetry(
                                    telemetry.speed,
                                    telemetry.rpm,
                                    telemetry.fuelLitres,
                                    now,
                                )
                            )
                            publishDistanceSinceRefuel(
                                distanceSinceRefuelTracker.advance(
                                    telemetry.speed,
                                    telemetry.fuelLitres,
                                    now,
                                )
                            )
                            telemetry.odometerKm?.takeIf { it > 0 }?.let {
                                _mileageFlow.value = it
                            }
                            _fuelFlow.value = telemetry.fuelLitres
                            _outsideTempFlow.value = telemetry.outsideTemperatureCelsius
                        }
                    }
                    93 -> { // Door Status
                        bArr?.let(TelemetryDecoder::decodeDoors)?.let {
                            _doorStatusFlow.value = it
                        }
                    }
                    91 -> { // Async notification for Handbrake / Seatbelts changed
                        if (replayActive && bArr != null && bArr.size >= 6) {
                            // In this capture bit 3 toggles with the parking-brake
                            // notification. Belt state is replayed independently
                            // from the captured SysVarProvider changes.
                            _parkingBrakeFlow.value = (bArr[5].toInt() and 0x08) != 0
                        } else {
                            updateVitalsFromProvider()
                        }
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): TelemetryService = this@TelemetryService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating TelemetryService...")
        startAsForegroundService()
        restoreTripSession()
        restoreDistanceSinceRefuel()
        startTripMetrics()
        runCatching {
            contentResolver.registerContentObserver(
                PROVIDER_URI.toUri(),
                true,
                providerObserver,
            )
        }.onFailure {
            Log.w(TAG, "SysVar observer unavailable: ${it.message}")
        }
        startEventServiceConnection(immediate = true)
        // The provider does not exist in the emulator. The capture did not store
        // its belt value, so replay starts with the belt buckled.
        if (BuildConfig.TELEMETRY_REPLAY_ENABLED && !isBound) {
            _seatbeltFlow.value = true
        } else {
            scope.launch { updateVitalsFromProvider() }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying TelemetryService...")
        shuttingDown = true
        eventConnectionJob?.cancel()
        eventConnectionJob = null
        persistTripSession()
        persistDistanceSinceRefuel()
        tripMetricsJob?.cancel()
        tripMetricsJob = null
        runCatching { contentResolver.unregisterContentObserver(providerObserver) }
        stopTelemetryReplay()
        unbindEventService()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForegroundService() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                TELEMETRY_CHANNEL_ID,
                getString(R.string.telemetry_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val openLauncher = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, TELEMETRY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.telemetry_notification_title))
            .setContentText(getString(R.string.telemetry_notification_text))
            .setContentIntent(openLauncher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(TELEMETRY_NOTIFICATION_ID, notification)
    }

    private fun restoreTripSession() {
        currentBootCount = runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(-1)
        val sameBoot = currentBootCount >= 0 &&
            tripPreferences.getInt(TRIP_BOOT_COUNT, -1) == currentBootCount &&
            isCompatibleTripSchema(tripPreferences.getInt(TRIP_SCHEMA, 0))
        val startedAt = if (sameBoot) {
            tripPreferences.getLong(TRIP_STARTED_AT, -1L).takeIf {
                it in 0L..SystemClock.elapsedRealtime()
            }
        } else {
            null
        }
        fun restoredDouble(key: String, fallback: Double = 0.0): Double =
            if (sameBoot) tripPreferences.getNonNegativeDoubleBits(key, fallback) else fallback
        val rangeConsumptionState = restoreRangeConsumptionState()
        lastPersistedRangeState = rangeConsumptionState
        tripSession = TripSessionTracker(
            TripSessionState(
                startedAtElapsedMs = startedAt,
                distanceKm = restoredDouble(TRIP_DISTANCE_BITS),
                fuelUsedLitres = restoredDouble(TRIP_FUEL_USED_BITS),
                virtualFuelLitres = restoredDouble(TRIP_VIRTUAL_FUEL_BITS),
                calibrationFactor = restoredDouble(TRIP_CALIBRATION_FACTOR_BITS, 1.0),
                lastFuelLitres = if (sameBoot) {
                    tripPreferences.getInt(TRIP_LAST_FUEL_LITRES, 0).takeIf { it > 0 }
                } else null,
                calibrationAnchorFuelLitres = if (sameBoot) {
                    tripPreferences.getInt(TRIP_CALIBRATION_ANCHOR_FUEL_LITRES, 0).takeIf { it > 0 }
                } else null,
                uncalibratedFuelSinceAnchorLitres = restoredDouble(TRIP_UNCALIBRATED_FUEL_BITS),
                recentConsumptionState = if (sameBoot) {
                    decodeRecentConsumptionState(tripPreferences.getString(TRIP_RECENT_CONSUMPTION, null))
                } else RecentConsumptionState(),
                rangeConsumptionState = rangeConsumptionState,
                rangeBaselineConsumption = if (sameBoot) {
                    tripPreferences.getNonNegativeDoubleBits(
                        TRIP_RANGE_BASELINE_BITS,
                        DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM,
                    )
                } else null,
            )
        )
        publishTripMetrics(tripSession.onTelemetry(0, 0, 0, SystemClock.elapsedRealtime()))
    }

    private fun startTripMetrics() {
        tripMetricsJob?.cancel()
        tripMetricsJob = scope.launch {
            var persistenceTicks = 0
            while (isActive) {
                delay(1_000L)
                publishTripMetrics(tripSession.onTick(SystemClock.elapsedRealtime()))
                publishDistanceSinceRefuel(
                    distanceSinceRefuelTracker.advance(
                        _speedFlow.value,
                        _fuelFlow.value,
                        SystemClock.elapsedRealtime(),
                        evaluateFuel = false,
                    )
                )
                persistenceTicks++
                if (persistenceTicks >= 10) {
                    persistTripSession()
                    persistDistanceSinceRefuel()
                    persistenceTicks = 0
                }
            }
        }
    }

    private fun publishTripMetrics(metrics: TripMetricsSnapshot) {
        _tripElapsedRealtimeMsFlow.value = metrics.elapsedMs
        _tripDistanceKmFlow.value = metrics.distanceKm
        _averageConsumptionFlow.value = metrics.averageConsumption
        _rangeFlow.value = displayedEstimatedRange(metrics.estimatedRangeKm)
    }

    private fun restoreDistanceSinceRefuel() {
        val distance = refuelPreferences.getNonNegativeDoubleBits(REFUEL_DISTANCE_BITS, 0.0)
        val lastFuel = refuelPreferences.getInt(REFUEL_LAST_FUEL_LITRES, 0).takeIf { it > 0 }
        distanceSinceRefuelTracker = DistanceSinceRefuelTracker(distance, lastFuel)
        publishDistanceSinceRefuel(
            distanceSinceRefuelTracker.advance(
                0,
                lastFuel ?: 0,
                SystemClock.elapsedRealtime(),
                evaluateFuel = false,
            )
        )
    }

    private fun publishDistanceSinceRefuel(snapshot: DistanceSinceRefuelSnapshot) {
        _distanceSinceRefuelKmFlow.value = snapshot.distanceKm
    }

    private fun persistDistanceSinceRefuel() {
        if (!::distanceSinceRefuelTracker.isInitialized) return
        val snapshot = distanceSinceRefuelTracker.advance(
            _speedFlow.value,
            _fuelFlow.value,
            SystemClock.elapsedRealtime(),
            evaluateFuel = false,
        )
        refuelPreferences.edit {
            putLong(REFUEL_DISTANCE_BITS, snapshot.distanceKm.toRawBits())
            snapshot.lastFuelLitres?.let { putInt(REFUEL_LAST_FUEL_LITRES, it) }
        }
    }

    private fun persistTripSession() {
        if (!::tripSession.isInitialized) return
        tripSession.onTick(SystemClock.elapsedRealtime())
        val version = tripSession.persistenceVersion()
        if (version == lastPersistedTripVersion) return
        val state = tripSession.state()
        if (currentBootCount >= 0) {
            tripPreferences.edit {
                putInt(TRIP_SCHEMA, CURRENT_TRIP_SCHEMA)
                putInt(TRIP_BOOT_COUNT, currentBootCount)
                putLong(TRIP_STARTED_AT, state.startedAtElapsedMs ?: -1L)
                putLong(TRIP_DISTANCE_BITS, state.distanceKm.toRawBits())
                putLong(TRIP_FUEL_USED_BITS, state.fuelUsedLitres.toRawBits())
                putLong(TRIP_VIRTUAL_FUEL_BITS, state.virtualFuelLitres.toRawBits())
                putLong(TRIP_CALIBRATION_FACTOR_BITS, state.calibrationFactor.toRawBits())
                state.lastFuelLitres?.let { putInt(TRIP_LAST_FUEL_LITRES, it) }
                state.calibrationAnchorFuelLitres?.let {
                    putInt(TRIP_CALIBRATION_ANCHOR_FUEL_LITRES, it)
                }
                putLong(
                    TRIP_UNCALIBRATED_FUEL_BITS,
                    state.uncalibratedFuelSinceAnchorLitres.toRawBits(),
                )
                putString(TRIP_RECENT_CONSUMPTION, state.recentConsumptionState.encode())
                putLong(
                    TRIP_RANGE_BASELINE_BITS,
                    (state.rangeBaselineConsumption ?: DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM)
                        .toRawBits(),
                )
            }
        }
        if (state.rangeConsumptionState != lastPersistedRangeState) {
            persistRangeConsumptionState(state.rangeConsumptionState)
            lastPersistedRangeState = state.rangeConsumptionState
        }
        lastPersistedTripVersion = version
    }

    private fun restoreRangeConsumptionState(): RangeConsumptionState {
        if (rangePreferences.getInt(RANGE_SCHEMA, 0) != CURRENT_RANGE_SCHEMA) {
            return RangeConsumptionState()
        }
        return RangeConsumptionState(
            learnedConsumption = rangePreferences.getNonNegativeDoubleBits(
                RANGE_LEARNED_CONSUMPTION_BITS,
                DEFAULT_RANGE_CONSUMPTION_L_PER_100_KM,
            ),
            pendingSegmentDistanceKm = rangePreferences.getNonNegativeDoubleBits(
                RANGE_PENDING_DISTANCE_BITS,
                0.0,
            ),
            pendingSegmentFuelLitres = rangePreferences.getNonNegativeDoubleBits(
                RANGE_PENDING_FUEL_BITS,
                0.0,
            ),
        )
    }

    private fun persistRangeConsumptionState(state: RangeConsumptionState) {
        rangePreferences.edit {
            putInt(RANGE_SCHEMA, CURRENT_RANGE_SCHEMA)
            putLong(RANGE_LEARNED_CONSUMPTION_BITS, state.learnedConsumption.toRawBits())
            putLong(RANGE_PENDING_DISTANCE_BITS, state.pendingSegmentDistanceKm.toRawBits())
            putLong(RANGE_PENDING_FUEL_BITS, state.pendingSegmentFuelLitres.toRawBits())
        }
    }

    private fun startEventServiceConnection(
        immediate: Boolean,
        failure: EventServiceFailure? = null,
    ) {
        if (shuttingDown || mEvtService != null || eventConnectionJob?.isActive == true) return
        eventConnectionJob = scope.launch {
            if (!immediate) {
                delay(reconnectPolicy.delayAfter(requireNotNull(failure)))
            }
            while (isActive && !shuttingDown && mEvtService == null) {
                disconnectEventService()
                if (attemptEventServiceBind()) return@launch
                if (BuildConfig.TELEMETRY_REPLAY_ENABLED) startTelemetryReplay()
                delay(reconnectPolicy.delayAfter(EventServiceFailure.BIND_REJECTED))
            }
        }
    }

    private fun attemptEventServiceBind(): Boolean = try {
            val bindIntent = Intent("com.szchoiceway.eventcenter.EventService").apply {
                setPackage("com.szchoiceway.eventcenter")
            }
            isBound = bindService(bindIntent, mSerCon, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "Binding attempt to EventService: $isBound")
            isBound
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to EventService: ${e.message}", e)
            isBound = false
            false
        }

    private fun handleEventServiceFailure(failure: EventServiceFailure) {
        if (shuttingDown) return
        eventConnectionJob?.cancel()
        eventConnectionJob = null
        disconnectEventService()
        if (BuildConfig.TELEMETRY_REPLAY_ENABLED) startTelemetryReplay()
        startEventServiceConnection(immediate = false, failure = failure)
    }

    private fun startTelemetryReplay() {
        if (replayJob?.isActive == true || mEvtService != null) return

        replayJob = scope.launch {
            replayActive = true
            Log.i(TAG, "Starting real-trip telemetry replay from $REPLAY_ASSET")

            while (isActive && mEvtService == null) {
                var replayedEvents = 0

                try {
                    val records = assets.open(REPLAY_ASSET).bufferedReader().useLines { lines ->
                        lines.mapNotNull(::parseReplayRecord).toList()
                    }
                    val timeline = ReplayTimeline.from(records.map(ReplayRecord::timestamp))
                        ?: error("Replay does not contain a complete timing timeline")
                    val cycleStartedAt = SystemClock.elapsedRealtime()
                    for (record in records) {
                        if (!record.shouldEmit) continue
                        val targetOffset = timeline.offsetMillis(record.timestamp) ?: continue
                        val remainingDelay = targetOffset -
                            (SystemClock.elapsedRealtime() - cycleStartedAt)
                        if (remainingDelay > 0L) delay(remainingDelay)
                        if (!isActive || mEvtService != null) break

                        if (record.isAidl) {
                            val bytes = hexToByteArray(record.json.optString("bytes_hex"))
                            mDashBoardCallback.notifyEvt(
                                record.json.optInt("msg_what"),
                                record.json.optInt("arg1"),
                                record.json.optInt("arg2"),
                                bytes,
                                record.json.optString("str")
                            )
                        } else {
                            val enabled = record.json.optString("value") == "1"
                            when (record.providerKey) {
                                KESAIWEI_RECORD_BELT -> _seatbeltFlow.value = enabled
                                KESAIWEI_RECORD_PARK -> _parkingBrakeFlow.value = enabled
                                KSW_DATA_SMALL_LIGHT_ON -> _lightsOnFlow.value = enabled
                            }
                        }
                        replayedEvents++
                    }
                    val remainingCycle = timeline.durationMillis -
                        (SystemClock.elapsedRealtime() - cycleStartedAt)
                    if (remainingCycle > 0L) delay(remainingCycle)
                    Log.i(TAG, "Telemetry replay completed: $replayedEvents callbacks")
                } catch (e: Exception) {
                    Log.e(TAG, "Telemetry replay failed: ${e.message}", e)
                    break
                }

                if (isActive && mEvtService == null) {
                    delay(REPLAY_LOOP_PAUSE_MS)
                }
            }
            replayActive = false
        }
    }

    private data class ReplayRecord(
        val json: JSONObject,
        val timestamp: ReplayTimestamp,
        val isAidl: Boolean,
        val providerKey: String,
        val shouldEmit: Boolean,
    )

    private fun parseReplayRecord(line: String): ReplayRecord? {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val source = json.optString("source")
        val providerKey = json.optString("key")
        val isAidl = source == "AIDL_CALLBACK"
        val isRelevantProviderState =
            (source == "SYSVAR_INITIAL" || source == "SYSVAR_CHANGE") &&
                (providerKey == KESAIWEI_RECORD_BELT ||
                    providerKey == KESAIWEI_RECORD_PARK ||
                    providerKey == KSW_DATA_SMALL_LIGHT_ON)
        val contributesToTimeline = isAidl || isRelevantProviderState || source == "GPS_LOCATION"
        if (!contributesToTimeline) return null
        return ReplayRecord(
            json = json,
            timestamp = ReplayTimestamp(
                timestampMillis = json.longOrNull("timestamp"),
                elapsedRealtimeNanos = json.longOrNull("elapsed_realtime_nanos"),
            ),
            isAidl = isAidl,
            providerKey = providerKey,
            shouldEmit = isAidl || isRelevantProviderState,
        )
    }

    private fun stopTelemetryReplay() {
        replayJob?.cancel()
        replayJob = null
        replayActive = false
    }

    private fun hexToByteArray(hex: String): ByteArray {
        if (hex.length % 2 != 0) return byteArrayOf()
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun unbindEventService() {
        eventConnectionJob?.cancel()
        eventConnectionJob = null
        disconnectEventService()
    }

    private fun disconnectEventService() {
        val wasBound = isBound
        isBound = false
        mEvtService = null
        if (!wasBound) return
        try {
            unbindService(mSerCon)
            Log.i(TAG, "Unbound from EventService.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding from EventService: ${e.message}")
        }
    }

    private fun updateVitalsFromProvider() {
        val parkingBrake = getRecordBooleanOrNull(KESAIWEI_RECORD_PARK)
        val seatbelt = getRecordBooleanOrNull(KESAIWEI_RECORD_BELT)
        val lightsOn = getRecordBooleanOrNull(KSW_DATA_SMALL_LIGHT_ON)
        _parkingBrakeFlow.value = retainLastKnownBoolean(_parkingBrakeFlow.value, parkingBrake)
        _seatbeltFlow.value = retainLastKnownBoolean(_seatbeltFlow.value, seatbelt)
        if (lightsOn != null) _lightsOnFlow.value = lightsOn
        Log.d(
            TAG,
            "Vitals updated: Handbrake=$parkingBrake, Seatbelt=$seatbelt, Lights=$lightsOn",
        )
    }

    private fun getRecordBooleanOrNull(key: String): Boolean? {
        val uri = PROVIDER_URI.toUri()
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, "keyname=?", arrayOf(key), null)
            if (cursor != null && cursor.count > 0 && cursor.moveToNext()) {
                val valueIndex = cursor.getColumnIndex("keyvalue")
                cursor.getString(valueIndex).toIntOrNull()?.let { it == 1 }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying ContentProvider for key $key: ${e.message}")
            null
        } finally {
            cursor?.close()
        }
    }

}
