package com.lito.a5launcher.assistant

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class NavigationTarget { WAZE, GOOGLE_MAPS }

class NavigationAction(private val context: Context) {
    fun open(request: NavigationRequest, preferredPackage: String?): Boolean {
        val target = targetFor(preferredPackage)
        val intent = when (request) {
            is NavigationRequest.Coordinates -> {
                if (DestinationValidator.validate(request.destination) !is DestinationValidation.Valid) return false
                intentFor(target, request.destination)
            }
            is NavigationRequest.SearchText -> {
                if (request.query.isBlank()) return false
                Intent(Intent.ACTION_VIEW, navigationSearchUrl(target, request.query).toUri())
                    .setPackage(packageFor(target))
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun targetFor(preferredPackage: String?): NavigationTarget = when (preferredPackage) {
        GOOGLE_MAPS_PACKAGE -> NavigationTarget.GOOGLE_MAPS
        else -> NavigationTarget.WAZE
    }

    private fun packageFor(target: NavigationTarget): String = when (target) {
        NavigationTarget.WAZE -> WAZE_PACKAGE
        NavigationTarget.GOOGLE_MAPS -> GOOGLE_MAPS_PACKAGE
    }

    internal fun intentFor(
        target: NavigationTarget,
        destination: NavigationDestination,
    ): Intent = when (target) {
        NavigationTarget.WAZE -> Intent(
            Intent.ACTION_VIEW,
            "https://waze.com/ul?ll=${destination.latitude},${destination.longitude}&navigate=yes".toUri(),
        ).setPackage(WAZE_PACKAGE)

        NavigationTarget.GOOGLE_MAPS -> Intent(
            Intent.ACTION_VIEW,
            "https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}".toUri(),
        ).setPackage(GOOGLE_MAPS_PACKAGE)
    }

    private companion object {
        const val WAZE_PACKAGE = "com.waze"
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}

internal fun navigationSearchUrl(target: NavigationTarget, query: String): String = when (target) {
    NavigationTarget.WAZE -> "https://waze.com/ul".toHttpUrl().newBuilder()
        .addQueryParameter("q", query.trim())
        .addQueryParameter("navigate", "yes")
        .build().toString()
    NavigationTarget.GOOGLE_MAPS -> "https://www.google.com/maps/dir/".toHttpUrl().newBuilder()
        .addQueryParameter("api", "1")
        .addQueryParameter("destination", query.trim())
        .build().toString()
}
