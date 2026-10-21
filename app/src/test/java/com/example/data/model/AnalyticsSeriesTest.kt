package com.example.data.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsSeriesTest {
  private val start = Instant.parse("2026-10-20T00:00:00Z")

  @Test fun documentedMetricsAreAnExplicitAllowlist() {
    assertEquals(
      listOf("status", "devices", "domains", "reasons", "destinations", "destinations", "dnssec", "encryption", "protocols", "ips", "queryTypes", "ipVersions"),
      SeriesMetric.entries.map(SeriesMetric::endpoint)
    )
    assertEquals("countries", SeriesMetric.COUNTRIES.type)
    assertEquals("gafam", SeriesMetric.GAFAM.type)
  }

  @Test fun seriesQueryRequiresValidRangeAndSupportedInterval() {
    assertTrue(query().validationError() == null)
    assertFalse(query(toExclusive = start).validationError() == null)
    assertFalse(query(intervalSeconds = 0).validationError() == null)
    assertFalse(query(intervalSeconds = 3601).validationError() == null)
  }

  @Test fun seriesQueryRejectsMoreThanFiveHundredBuckets() {
    val tooWide = AnalyticsSeriesQuery(
      profileId = "abcdef",
      deviceId = null,
      fromInclusive = start,
      toExclusive = start.plus(Duration.ofDays(501)),
      metric = SeriesMetric.STATUS,
      intervalSeconds = 86_400
    )
    assertFalse(tooWide.validationError() == null)
  }

  @Test fun domainOnlyFiltersCannotLeakToOtherMetricRequests() {
    assertTrue(query(metric = SeriesMetric.DOMAINS, domainStatus = "blocked", root = true).validationError() == null)
    assertFalse(query(metric = SeriesMetric.STATUS, domainStatus = "blocked").validationError() == null)
    assertFalse(query(metric = SeriesMetric.DOMAINS, domainStatus = "unknown").validationError() == null)
    assertFalse(query(metric = SeriesMetric.STATUS, root = true).validationError() == null)
  }

  private fun query(
    toExclusive: Instant = start.plusSeconds(3_600),
    intervalSeconds: Long = 3_600,
    metric: SeriesMetric = SeriesMetric.STATUS,
    domainStatus: String? = null,
    root: Boolean = false
  ) = AnalyticsSeriesQuery(
    profileId = "abcdef",
    deviceId = null,
    fromInclusive = start,
    toExclusive = toExclusive,
    metric = metric,
    intervalSeconds = intervalSeconds,
    domainStatus = domainStatus,
    root = root
  )
}

