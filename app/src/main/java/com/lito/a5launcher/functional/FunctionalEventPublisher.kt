package com.lito.a5launcher.functional

class FunctionalEventPublisher(
    private val settings: FunctionalEventSettings,
    private val sink: (FunctionalEventDraft) -> Boolean,
) {
    fun publish(draft: FunctionalEventDraft): Boolean {
        if (!settings.snapshot().captures(draft.category)) return false
        return runCatching { sink(draft) }.getOrDefault(false)
    }
}
