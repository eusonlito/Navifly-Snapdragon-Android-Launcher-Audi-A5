package com.lito.a5launcher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.lito.a5launcher.model.AppInfo
import com.lito.a5launcher.model.DoorStatus
import com.lito.a5launcher.assistant.NavigationAction
import com.lito.a5launcher.assistant.NavigationRequest
import com.lito.a5launcher.functional.FunctionalEventLogAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

internal class NavigationLaunchGate(private val cooldownMs: Long = 5_000L) {
    private var lockedUntilElapsedMs = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(nowElapsedMs: Long): Boolean {
        if (nowElapsedMs < lockedUntilElapsedMs) return false
        lockedUntilElapsedMs = nowElapsedMs + cooldownMs
        return true
    }

    @Synchronized
    fun remainingMs(nowElapsedMs: Long): Long =
        (lockedUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LauncherViewModel"
        private const val SYS_VAR_URI =
            "content://com.szchoiceway.eventcenter.SysVarProvider/SysVar"
        private const val NAV_PACKAGE_KEY = "NAV_PACKAGENAME"
        private const val NAV_ACTIVITY_KEY = "NAV_ACTIVITYNAME"
    }

    @Suppress("StaticFieldLeak")
    private val context = application.applicationContext
    private val navigationAction = NavigationAction(context)
    @Volatile private var configuredNavigationPackage = "com.waze"
    // Mirroring StateFlows from TelemetryService (handling unbound state gracefully)
    private val _speed = MutableStateFlow(0)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    private val _rpm = MutableStateFlow(0)
    val rpm: StateFlow<Int> = _rpm.asStateFlow()

    private val _doorStatus = MutableStateFlow(DoorStatus())
    val doorStatus: StateFlow<DoorStatus> = _doorStatus.asStateFlow()

    private val _fuel = MutableStateFlow(0)
    val fuel: StateFlow<Int> = _fuel.asStateFlow()

    private val _mileage = MutableStateFlow(0)
    val mileage: StateFlow<Int> = _mileage.asStateFlow()

    private val _range = MutableStateFlow(0) // Unknown until a validated native value arrives
    val range: StateFlow<Int> = _range.asStateFlow()

    private val _outsideTemp = MutableStateFlow(Double.NaN)
    val outsideTemp: StateFlow<Double> = _outsideTemp.asStateFlow()

    private val _gear = MutableStateFlow("—")
    val gear: StateFlow<String> = _gear.asStateFlow()

    private val _tripStatistics = MutableStateFlow(JourneyStatisticsSnapshot())
    val tripStatistics: StateFlow<JourneyStatisticsSnapshot> = _tripStatistics.asStateFlow()

    private val _partialStatistics = MutableStateFlow(JourneyStatisticsSnapshot())
    val partialStatistics: StateFlow<JourneyStatisticsSnapshot> = _partialStatistics.asStateFlow()

    private val _navigationLaunchLocked = MutableStateFlow(false)
    val navigationLaunchLocked: StateFlow<Boolean> = _navigationLaunchLocked.asStateFlow()
    private var navigationLaunchJob: Job? = null
    private val navigationLaunchGate = NavigationLaunchGate()

    // Avoid displaying a false warning before the provider returns its first state.
    private val _seatbelt = MutableStateFlow(true)
    val seatbelt: StateFlow<Boolean> = _seatbelt.asStateFlow()

    private val _parkingBrake = MutableStateFlow(false)
    val parkingBrake: StateFlow<Boolean> = _parkingBrake.asStateFlow()

    private val _lightsOn = MutableStateFlow<Boolean?>(null)
    val lightsOn: StateFlow<Boolean?> = _lightsOn.asStateFlow()

    private val _functionalEventLogAccess = MutableStateFlow<FunctionalEventLogAccess?>(null)
    internal val functionalEventLogAccess: StateFlow<FunctionalEventLogAccess?> =
        _functionalEventLogAccess.asStateFlow()

    // App Drawer list
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    @Suppress("StaticFieldLeak")
    private var telemetryService: TelemetryService? = null
    private var isBound = false
    private var collectionJobs = mutableListOf<Job>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Bound to TelemetryService successfully!")
            val binder = service as TelemetryService.LocalBinder
            telemetryService = binder.getService()
            isBound = true

            // Cancel any previous collection jobs
            collectionJobs.forEach { it.cancel() }
            collectionJobs.clear()

            // Start collecting from service flows in parallel
            telemetryService?.let { svc ->
                _functionalEventLogAccess.value = FunctionalEventLogAccess(
                    svc.functionalEventJournal,
                    svc.functionalEventSettings,
                )
                collectionJobs.add(viewModelScope.launch { svc.speedFlow.collect { _speed.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.rpmFlow.collect { _rpm.value = it } })
                collectionJobs.add(viewModelScope.launch {
                    svc.calculatedGearFlow.collect { _gear.value = it }
                })
                collectionJobs.add(viewModelScope.launch { svc.doorStatusFlow.collect { _doorStatus.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.fuelFlow.collect { _fuel.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.mileageFlow.collect { _mileage.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.rangeFlow.collect { _range.value = it } })
                collectionJobs.add(viewModelScope.launch {
                    svc.tripStatisticsFlow.collect { _tripStatistics.value = it }
                })
                collectionJobs.add(viewModelScope.launch {
                    svc.partialStatisticsFlow.collect { _partialStatistics.value = it }
                })
                collectionJobs.add(viewModelScope.launch { svc.outsideTempFlow.collect { _outsideTemp.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.seatbeltFlow.collect { _seatbelt.value = it } })
                collectionJobs.add(viewModelScope.launch { svc.parkingBrakeFlow.collect {
                    _parkingBrake.value = it
                } })
                collectionJobs.add(viewModelScope.launch {
                    svc.lightsOnFlow.collect { _lightsOn.value = it }
                })
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "Unbound/Disconnected from TelemetryService.")
            isBound = false
            telemetryService = null
            _functionalEventLogAccess.value = null
            collectionJobs.forEach { it.cancel() }
            collectionJobs.clear()
        }
    }

    init {
        // Query installed applications on Dispatchers.Default
        queryInstalledApps()
        viewModelScope.launch(Dispatchers.IO) {
            configuredNavigationPackage = getSystemRecord(NAV_PACKAGE_KEY).ifEmpty { "com.waze" }
        }

        // Bind to TelemetryService
        bindTelemetryService()
    }

    private fun bindTelemetryService() {
        try {
            val intent = Intent(context, TelemetryService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind TelemetryService: ${e.message}")
        }
    }

    private fun queryInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                val apps = resolveInfos.mapNotNull { info ->
                    val packageName = info.activityInfo.packageName
                    if (packageName == context.packageName) return@mapNotNull null // ignore this launcher

                    val label = info.loadLabel(pm).toString()
                    val icon = info.loadIcon(pm)
                    AppInfo(label, packageName, icon)
                }.sortedBy { it.label.lowercase(Locale.getDefault()) }

                _installedApps.value = apps
                Log.i(TAG, "Successfully queried ${apps.size} launcher apps.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query installed apps: ${e.message}")
            }
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Log.e(TAG, "Unable to get launch intent for package: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app $packageName: ${e.message}")
            }
        }
    }

    fun openAppInfo(packageName: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open app info for $packageName: ${e.message}")
            }
        }
    }

    fun launchWaze() {
        val startedAt = SystemClock.elapsedRealtime()
        if (navigationLaunchJob?.isActive == true || !navigationLaunchGate.tryAcquire(startedAt)) return
        _navigationLaunchLocked.value = true
        navigationLaunchJob = viewModelScope.launch {
            try {
                val configured = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    getSystemRecord(NAV_PACKAGE_KEY) to getSystemRecord(NAV_ACTIVITY_KEY)
                }
                val navPackage = configured.first.ifEmpty { "com.waze" }
                val navActivity = configured.second
                val intent = if (navActivity.isNotEmpty()) {
                    Intent().setComponent(ComponentName(navPackage, navActivity))
                } else {
                    context.packageManager.getLaunchIntentForPackage(navPackage)
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    Log.e(TAG, "Configured navigation app is unavailable: $navPackage")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch configured navigation: ${e.message}")
            } finally {
                val remaining = navigationLaunchGate.remainingMs(SystemClock.elapsedRealtime())
                if (remaining > 0L) delay(remaining)
                _navigationLaunchLocked.value = false
            }
        }
    }

    fun launchNavigation(request: NavigationRequest): Boolean =
        navigationAction.open(request, configuredNavigationPackage)

    fun launchSettings() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent().setComponent(
                    ComponentName(
                        "com.szchoiceway.settings",
                        "com.szchoiceway.settings.MainActivity"
                    )
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Szchoiceway settings unavailable, opening Android settings")
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    fun launchOriginalMMI() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(
                    "com.szchoiceway.eventcenter.EventUtils.ACTION_SWITCH_ORIGINACAR"
                ).apply {
                    setPackage("com.szchoiceway.eventcenter")
                }
                context.sendBroadcast(intent)
                Log.i(TAG, "Dispatched original MMI change broadcast.")
            } catch (e: Exception) {
                Log.e(TAG, "Error launching original MMI: ${e.message}")
            }
        }
    }

    fun openRecentApps() {
        viewModelScope.launch(Dispatchers.Main) {
            if (RecentAppsAccessibilityService.openNativeRecentApps()) return@launch

            Toast.makeText(
                context,
                context.getString(R.string.enable_recent_apps_service),
                Toast.LENGTH_LONG,
            ).show()
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun getSystemRecord(key: String): String {
        return try {
            context.contentResolver.query(
                SYS_VAR_URI.toUri(),
                null,
                "keyname=?",
                arrayOf(key),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex("keyvalue")
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                } else {
                    ""
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "System record unavailable for $key: ${e.message}")
            ""
        }
    }

    override fun onCleared() {
        collectionJobs.forEach { it.cancel() }
        collectionJobs.clear()
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service on ViewModel clear: ${e.message}")
        }
    }
}
