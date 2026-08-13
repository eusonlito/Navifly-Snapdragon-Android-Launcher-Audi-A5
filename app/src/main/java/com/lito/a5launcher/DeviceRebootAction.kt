package com.lito.a5launcher

import android.content.Context
import android.content.Intent

internal object DeviceRebootAction {
    internal const val EVENT_CENTER_PACKAGE = "com.szchoiceway.eventcenter"
    internal const val ACTION_REBOOT_DEVICE = "com.szchoiceway.action.reboot"

    fun request(context: Context): Result<Unit> = runCatching {
        context.sendBroadcast(createIntent())
    }

    internal fun createIntent(): Intent =
        Intent(ACTION_REBOOT_DEVICE).setPackage(EVENT_CENTER_PACKAGE)
}
