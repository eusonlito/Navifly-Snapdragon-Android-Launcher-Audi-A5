package com.lito.a5launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class RecentAppsAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        activeService = this
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var activeService: RecentAppsAccessibilityService? = null

        fun openNativeRecentApps(): Boolean =
            activeService?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
    }
}
