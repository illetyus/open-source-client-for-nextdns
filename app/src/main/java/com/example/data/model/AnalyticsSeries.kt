package com.example.data.model

import java.time.Duration
import java.time.Instant

/** Every endpoint used by series charts is selected from this documented allowlist. */
enum class SeriesMetric(val endpoint: String, val type: String? = null) {
  STATUS("status"),
  DEVICES("devices"),
  DOMAINS("domains"),
  REASONS("reasons"),
  COUNTRIES("destinations", "countries"),
  GAFAM("destinations", "gafam"),
  DNSSEC("dnssec"),
  ENCRYPTION("encryption"),
  PROTOCOLS("protocols"),
  IPS("ips"),
  QUERY_TYPES("queryTypes"),
  IP_VERSIONS("ipVersions")
}

data class AnalyticsSeriesQuery(
  val profileId: String,
  val deviceId: String?,
  val fromInclusive: Instant,
  val toExclusive: Instant,
  val metric: SeriesMetric,
  val intervalSeconds: Long,
  val domainStatus: String? = null,
  val root: Boolean = false
) {
  fun validationError(): String? {
    if (profileId.isBlank()) return "missing_profile"
    if (!fromInclusive.isBefore(toExclusive)) return "invalid_range"
    if (intervalSeconds !in SUPPORTED_INTERVALS_SECONDS) return "invalid_interval"
    if (domainStatus != null && (metric != SeriesMetric.DOMAINS || domainStatus !in DOMAIN_STATUSES)) {
      return "invalid_domain_status"
    }
    if (root && metric != SeriesMetric.DOMAINS) return "invalid_root_filter"
    val durationSeconds = runCatching { Duration.between(fromInclusive, toExclusive).seconds }.getOrNull()
      ?: return "invalid_range"
    if (durationSeconds <= 0) return "invalid_range"
    val bucketCount = (durationSeconds - 1) / intervalSeconds + 1
    if (bucketCount > MAX_TIME_BUCKETS) return "range_too_wide"
    return null
  }

  companion object {
    val SUPPORTED_INTERVALS_SECONDS = setOf(3_600L, 86_400L)
    val DOMAIN_STATUSES = setOf("default", "blocked", "allowed")
    const val MAX_TIME_BUCKETS = 500
    const val MAX_ENTITIES = 50
  }
}

data class SeriesPoint(val at: Instant, val queries: Long?)

data class SeriesRow(val key: String, val label: String, val points: List<SeriesPoint>)

data class AnalyticsSeriesState(
  val query: AnalyticsSeriesQuery? = null,
  val rows: List<SeriesRow> = emptyList(),
  val intervalSeconds: Long? = null,
  val loading: Boolean = false,
  val errorMessage: String? = null,
  val fetchedAt: Instant? = null
)

