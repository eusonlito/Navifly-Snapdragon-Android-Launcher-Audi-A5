package com.lito.a5launcher

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val languageTag: String) {
    SPANISH("es"),
    ENGLISH("en"),
}

object AppLanguageManager {
    private const val PREFERENCES = "launcher_language"
    private const val KEY_INITIALIZED = "initialized"

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_INITIALIZED, false)) return

        val initial = initialLanguage(Locale.getDefault())
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(initial.languageTag)
        preferences.edit { putBoolean(KEY_INITIALIZED, true) }
    }

    fun current(context: Context): AppLanguage {
        val tag = context.getSystemService(LocaleManager::class.java)
            .applicationLocales
            .get(0)
            ?.language
        return AppLanguage.entries.firstOrNull { it.languageTag == tag }
            ?: initialLanguage(Locale.getDefault())
    }

    fun select(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_INITIALIZED, true) }
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(language.languageTag)
    }
}

internal fun initialLanguage(systemLocale: Locale): AppLanguage =
    if (systemLocale.language == AppLanguage.SPANISH.languageTag) {
        AppLanguage.SPANISH
    } else {
        AppLanguage.ENGLISH
    }
