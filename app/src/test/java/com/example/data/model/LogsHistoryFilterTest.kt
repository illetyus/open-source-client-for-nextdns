package com.example.data.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogsHistoryFilterTest {
  @Test fun rangeUsesInclusiveFromExclusiveTo() {
    val window = LogsTimeWindow.fromLocalDates(
      startInclusive = LocalDate.parse("2026-02-01"),
      endInclusive = LocalDate.parse("2026-02-03"),
      zoneId = ZoneId.of("America/Los_Angeles")
    )

    assertNotNull(window)
    assertEquals(Instant.parse("2026-02-01T08:00:00Z"), window!!.fromInclusive)
    assertEquals(Instant.parse("2026-02-04T08:00:00Z"), window.toExclusive)
    assertEquals("2026-02-01T08:00:00Z", window.fromQueryValue())
    assertEquals("2026-02-04T08:00:00Z", window.toQueryValue())
  }

  @Test fun localDateRangeSurvivesDst() {
    val zone = ZoneId.of("Europe/Berlin")
    val springDay = LogsTimeWindow.fromLocalDates(
      startInclusive = LocalDate.parse("2026-03-29"),
      endInclusive = LocalDate.parse("2026-03-29"),
      zoneId = zone
    )!!
    val autumnDay = LogsTimeWindow.fromLocalDates(
      startInclusive = LocalDate.parse("2026-10-25"),
      endInclusive = LocalDate.parse("2026-10-25"),
      zoneId = zone
    )!!

    assertEquals(Duration.ofHours(23), Duration.between(springDay.fromInclusive, springDay.toExclusive))
    assertEquals(Duration.ofHours(25), Duration.between(autumnDay.fromInclusive, autumnDay.toExclusive))
  }

  @Test fun reversedDateRangeIsRejected() {
    assertNull(LogsTimeWindow.fromLocalDates(
      startInclusive = LocalDate.parse("2026-05-02"),
      endInclusive = LocalDate.parse("2026-05-01"),
      zoneId = ZoneId.of("UTC")
    ))
    assertFalse(LogsTimeWindow(
      fromInclusive = Instant.parse("2026-05-02T00:00:00Z"),
      toExclusive = Instant.parse("2026-05-01T00:00:00Z")
    ).isValid())
  }

  @Test fun onlyUnboundedNewestHistorySupportsLiveStreaming() {
    assertTrue(LogsHistoryFilter().supportsLiveStreaming)
    assertFalse(LogsHistoryFilter(sort = LogsSort.OLDEST_FIRST).supportsLiveStreaming)
    assertFalse(LogsHistoryFilter(
      window = LogsTimeWindow(fromInclusive = Instant.parse("2026-05-01T00:00:00Z"))
    ).supportsLiveStreaming)
    assertEquals("asc", LogsSort.OLDEST_FIRST.apiValue)
    assertEquals("desc", LogsSort.NEWEST_FIRST.apiValue)
  }
}
