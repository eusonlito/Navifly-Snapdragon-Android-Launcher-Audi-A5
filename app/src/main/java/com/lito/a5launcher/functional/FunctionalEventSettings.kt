package com.lito.a5launcher.functional

import android.content.SharedPreferences
import androidx.core.content.edit

data class FunctionalEventSettingsSnapshot(
    val enabled: Boolean,
    val categories: Set<FunctionalEventCategory>,
) {
    fun captures(category: FunctionalEventCategory): Boolean = enabled && category in categories
}

interface FunctionalEventPreferenceStore {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class SharedPreferencesFunctionalEventStore(
    private val preferences: SharedPreferences,
) : FunctionalEventPreferenceStore {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }
}

class FunctionalEventSettings(
    private val store: FunctionalEventPreferenceStore,
) {
    fun snapshot(): FunctionalEventSettingsSnapshot = FunctionalEventSettingsSnapshot(
        enabled = store.getBoolean(GLOBAL_KEY, false),
        categories = FunctionalEventCategory.entries.filterTo(mutableSetOf()) {
            store.getBoolean(categoryKey(it), true)
        },
    )

    fun setEnabled(enabled: Boolean) = store.putBoolean(GLOBAL_KEY, enabled)

    fun setCategoryEnabled(category: FunctionalEventCategory, enabled: Boolean) =
        store.putBoolean(categoryKey(category), enabled)

    private fun categoryKey(category: FunctionalEventCategory) = "category.${category.code}"

    private companion object {
        const val GLOBAL_KEY = "enabled"
    }
}
