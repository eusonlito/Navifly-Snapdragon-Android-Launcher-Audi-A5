package com.lito.a5launcher

internal data class PartialStatistics(
    val records: List<JourneyHistoryRecord>,
    val distanceKm: Double,
    val estimatedConsumption: Double?,
    val canConsumption: Double?,
)

internal const val PARTIALS_PER_PAGE = 6

/** Page zero contains the latest six partials, in chronological order. */
internal fun partialStatisticsPage(records: List<JourneyHistoryRecord>, page: Int): List<JourneyHistoryRecord> {
    val lastPage = (records.size - 1).coerceAtLeast(0) / PARTIALS_PER_PAGE
    val end = records.size - page.coerceIn(0, lastPage) * PARTIALS_PER_PAGE
    return records.subList((end - PARTIALS_PER_PAGE).coerceAtLeast(0), end)
}

internal fun partialEstimatedConsumption(statistics: JourneyStatisticsSnapshot): Double? =
    if (statistics.distanceKm > 0.0) statistics.fuelUsedLitres / statistics.distanceKm * 100.0 else null

internal fun partialCanConsumption(statistics: JourneyStatisticsSnapshot): Double? =
    if (statistics.distanceKm > 0.0 && statistics.confirmedCanFuelUsedLitres > 0.0) {
        statistics.confirmedCanFuelUsedLitres / statistics.distanceKm * 100.0
    } else null

/** Computed only by the statistics screen; nothing is persisted or added to telemetry processing. */
internal fun buildPartialStatistics(
    records: List<JourneyHistoryRecord>,
    fromEpochMs: Long? = null,
    untilEpochMs: Long? = null,
): PartialStatistics {
    val partials = records.filter {
        it.kind == JourneyHistoryKind.PARTIAL &&
            (fromEpochMs == null || it.endedAtEpochMs >= fromEpochMs) &&
            (untilEpochMs == null || it.startedAtEpochMs < untilEpochMs)
    }.sortedWith(compareBy<JourneyHistoryRecord> { it.endedAtEpochMs }.thenBy { it.id })
    val distance = partials.sumOf { it.statistics.distanceKm }
    val estimated = partials.filter { it.statistics.distanceKm > 0.0 }
    val can = partials.filter { partialCanConsumption(it.statistics) != null }
    val canDistance = can.sumOf { it.statistics.distanceKm }
    return PartialStatistics(
        records = partials,
        distanceKm = distance,
        estimatedConsumption = if (distance > 0.0) estimated.sumOf { it.statistics.fuelUsedLitres } / distance * 100 else null,
        canConsumption = if (canDistance > 0.0) can.sumOf { it.statistics.confirmedCanFuelUsedLitres } / canDistance * 100 else null,
    )
}
