package com.lito.a5launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.lito.a5launcher.ui.components.DashboardScreen
import com.lito.a5launcher.ui.components.MapDebugLogger
import com.lito.a5launcher.ui.theme.A5LauncherTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var crashHandlerInstalled = false
    }

    private val viewModel: LauncherViewModel by viewModels()
    private val activityId = "A-${UUID.randomUUID().toString().take(8)}"
    private val lifecycleLogger by lazy { MapDebugLogger.get(this) }
    private var dashboardShown = false
    private var unlockReceiver: BroadcastReceiver? = null
    private val unlockHandler = Handler(Looper.getMainLooper())
    private val unlockCheck = object : Runnable {
        override fun run() {
            if (dashboardShown) return
            if (getSystemService(UserManager::class.java).isUserUnlocked) {
                logLifecycle("USER_UNLOCKED | origen=verificación")
                unregisterUnlockReceiver()
                showDashboard()
            } else {
                unlockHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 1. Force screen to stay on while driving / showing dashboard
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val userManager = getSystemService(UserManager::class.java)
        if (userManager.isUserUnlocked) {
            installCrashDiagnostics()
            logLifecycle(
                "CREATE | task=$taskId | saved=${savedInstanceState != null}" +
                    " | intent=${intent.lifecycleDescription()}"
            )
            showDashboard()
        } else {
            showDirectBootPlaceholder()
            waitForUserUnlock()
        }

        // Hide Android chrome after either the placeholder or dashboard is bound.
        setupImmersiveFullscreen()
    }

    private fun showDirectBootPlaceholder() {
        setContent {
            A5LauncherTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }

    private fun waitForUserUnlock() {
        if (unlockReceiver != null) return
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    unregisterUnlockReceiver()
                    showDashboard()
                }
            }
        }.also { receiver ->
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                // Some automotive firmwares deliver protected system broadcasts
                // from highly privileged packages rather than the system UID.
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
        unlockHandler.removeCallbacks(unlockCheck)
        unlockHandler.post(unlockCheck)
    }

    private fun showDashboard() {
        if (dashboardShown) return
        // The dashboard and diagnostics use credential-protected storage.
        // They must never be initialized by the Direct Boot placeholder.
        installCrashDiagnostics()
        AppLanguageManager.initialize(this)
        dashboardShown = true
        setContent {
            A5LauncherTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun unregisterUnlockReceiver() {
        unlockHandler.removeCallbacks(unlockCheck)
        unlockReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        unlockReceiver = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveFullscreen()
        }
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("RESUME | task=$taskId")
        showDashboardWhenUnlocked()
    }

    override fun onDestroy() {
        logLifecycle(
            "DESTROY | task=$taskId | finishing=$isFinishing" +
                " | configuración=$isChangingConfigurations"
        )
        unregisterUnlockReceiver()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("START | task=$taskId")
        showDashboardWhenUnlocked()
    }

    override fun onPause() {
        logLifecycle("PAUSE | task=$taskId")
        super.onPause()
    }

    override fun onStop() {
        logLifecycle("STOP | task=$taskId | finishing=$isFinishing")
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logLifecycle("NEW_INTENT | task=$taskId | intent=${intent.lifecycleDescription()}")
        showDashboardWhenUnlocked()
    }

    override fun onTrimMemory(level: Int) {
        logLifecycle("TRIM_MEMORY | nivel=$level")
        super.onTrimMemory(level)
    }

    private fun logLifecycle(event: String) {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return
        val enabled = getSharedPreferences("launcher_settings", MODE_PRIVATE)
            .getBoolean("map_debug", false)
        lifecycleLogger.setEnabled(enabled)
        if (enabled) {
            lifecycleLogger.write("$activityId ACTIVITY $event | pid=${android.os.Process.myPid()}")
        }
    }

    private fun showDashboardWhenUnlocked() {
        if (
            !dashboardShown &&
            getSystemService(UserManager::class.java).isUserUnlocked
        ) {
            unregisterUnlockReceiver()
            showDashboard()
        }
    }

    private fun installCrashDiagnostics() {
        if (crashHandlerInstalled) return
        synchronized(MainActivity::class.java) {
            if (crashHandlerInstalled) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val crashLogger = MapDebugLogger.get(applicationContext)
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                crashLogger.writeImmediately(
                    "CRASH NO CAPTURADO | hilo=${thread.name}" +
                        " | ${error.stackTraceToString()}"
                )
                previous?.uncaughtException(thread, error)
            }
            crashHandlerInstalled = true
        }
    }

    private fun setupImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = window.peekDecorView()
            if (decorView != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}

private fun Intent?.lifecycleDescription(): String {
    if (this == null) return "null"
    return "action=${action.orEmpty()},categories=${categories?.sorted()?.joinToString().orEmpty()}"
}
