package com.example.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogsSort(val apiValue: String) {
  NEWEST_FIRST("desc"),
  OLDEST_FIRST("asc")
}

enum class LogsHistoryPreset {
  AVAILABLE,
  LAST_24_HOURS,
  LAST_7_DAYS,
  CUSTOM
}

/** A half-open time window: from is inclusive and to is exclusive. */
data class LogsTimeWindow(
  val fromInclusive: Instant? = null,
  val toExclusive: Instant? = null
) {
  fun isValid(): Boolean = fromInclusive == null || toExclusive == null || fromInclusive < toExclusive

  fun fromQueryValue(): String? = fromInclusive?.let(DateTimeFormatter.ISO_INSTANT::format)

  fun toQueryValue(): String? = toExclusive?.let(DateTimeFormatter.ISO_INSTANT::format)

  companion object {
    /** Converts inclusive calendar dates in the user's zone into an API half-open UTC interval. */
    fun fromLocalDates(
      startInclusive: LocalDate?,
      endInclusive: LocalDate?,
      zoneId: ZoneId
    ): LogsTimeWindow? = runCatching {
      LogsTimeWindow(
        fromInclusive = startInclusive?.atStartOfDay(zoneId)?.toInstant(),
        toExclusive = endInclusive?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()
      )
    }.getOrNull()?.takeIf(LogsTimeWindow::isValid)
  }
}

data class LogsHistoryFilter(
  val window: LogsTimeWindow = LogsTimeWindow(),
  val sort: LogsSort = LogsSort.NEWEST_FIRST,
  val preset: LogsHistoryPreset = LogsHistoryPreset.AVAILABLE
) {
  fun isValid(): Boolean = window.isValid()

  /** The live endpoint cannot honor date bounds or ascending order. */
  val supportsLiveStreaming: Boolean
    get() = window.fromInclusive == null && window.toExclusive == null && sort == LogsSort.NEWEST_FIRST
}
