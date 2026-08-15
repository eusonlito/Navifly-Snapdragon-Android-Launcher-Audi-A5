package com.lito.a5launcher.functional

/** Narrow service-owned access passed to the settings UI after binding. */
data class FunctionalEventLogAccess(
    val journal: FunctionalEventJournal,
    val settings: FunctionalEventSettings,
)
