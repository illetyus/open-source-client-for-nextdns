package com.example.data.api

import com.example.data.model.AnalyticsSeriesQuery
import com.example.data.model.SeriesMetric
import com.example.data.model.SeriesPoint
import com.example.data.model.SeriesRow
import java.time.Instant
import java.time.OffsetDateTime

data class ApiSeriesMeta(
  val times: List<String>? = null,
  val interval: Long? = null
)

data class AnalyticsSeriesDto(
  val status: String? = null,
  val id: String? = null,
  val name: String? = null,
  val model: String? = null,
  val domain: String? = null,
  val root: String? = null,
  val company: String? = null,
  val code: String? = null,
  val validated: Boolean? = null,
  val encrypted: Boolean? = null,
  val protocol: String? = null,
  val ip: String? = null,
  val type: Int? = null,
  val version: Int? = null,
  val queries: List<Long?>? = null
)

object AnalyticsSeriesMapper {
  fun parse(
    query: AnalyticsSeriesQuery,
    meta: ApiSeriesMeta?,
    data: List<AnalyticsSeriesDto>?
  ): List<SeriesRow> {
    require(query.validationError() == null) { "Invalid analytics series query" }
    val seriesMeta = requireNotNull(meta) { "Missing analytics series metadata" }
    val interval = requireNotNull(seriesMeta.interval) { "Missing analytics series interval" }
    require(interval > 0) { "Invalid analytics series interval" }
    require(interval == query.intervalSeconds) { "Unexpected analytics series interval" }
    val times = requireNotNull(seriesMeta.times) { "Missing analytics series times" }
      .map { raw -> parseInstant(raw) ?: throw IllegalArgumentException("Invalid analytics series time") }
    require(times.zipWithNext().all { (left, right) -> left.isBefore(right) }) {
      "Analytics series times are not strictly increasing"
    }
    require(times.size <= AnalyticsSeriesQuery.MAX_TIME_BUCKETS) { "Analytics series exceeds bucket limit" }
    val items = requireNotNull(data) { "Missing analytics series data" }
    require(items.size <= AnalyticsSeriesQuery.MAX_ENTITIES) { "Analytics series exceeds entity limit" }
    if (items.isEmpty()) return emptyList()
    require(times.isNotEmpty()) { "Empty analytics series time axis" }

    return items.map { item ->
      val counts = requireNotNull(item.queries) { "Missing analytics series counts" }
      require(counts.size == times.size) { "Analytics series count length mismatch" }
      require(counts.all { it == null || it >= 0L }) { "Negative analytics series count" }
      val (key, label) = entityIdentity(query.metric, query.root, item)
        ?: throw IllegalArgumentException("Missing analytics series entity identity")
      SeriesRow(key, label, times.indices.map { index -> SeriesPoint(times[index], counts[index]) })
    }
  }

  private fun parseInstant(raw: String): Instant? =
    runCatching { Instant.parse(raw) }.getOrElse { runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() }

  private fun entityIdentity(metric: SeriesMetric, root: Boolean, item: AnalyticsSeriesDto): Pair<String, String>? {
    val preferred = when (metric) {
      SeriesMetric.STATUS -> item.status
      SeriesMetric.DEVICES -> item.id ?: item.name
      SeriesMetric.DOMAINS -> if (root) item.root ?: item.domain else item.domain
      SeriesMetric.REASONS -> item.id ?: item.name
      SeriesMetric.COUNTRIES -> item.code
      SeriesMetric.GAFAM -> item.company
      SeriesMetric.DNSSEC -> item.validated?.toString()
      SeriesMetric.ENCRYPTION -> item.encrypted?.toString()
      SeriesMetric.PROTOCOLS -> item.protocol
      SeriesMetric.IPS -> item.ip
      SeriesMetric.QUERY_TYPES -> item.type?.toString() ?: item.name
      SeriesMetric.IP_VERSIONS -> item.version?.toString()
    }?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val label = when (metric) {
      SeriesMetric.DEVICES, SeriesMetric.REASONS -> item.name?.trim()?.takeIf(String::isNotEmpty) ?: preferred
      SeriesMetric.DOMAINS -> (if (root) item.root else item.domain)?.trim()?.takeIf(String::isNotEmpty) ?: preferred
      SeriesMetric.COUNTRIES -> item.name?.trim()?.takeIf(String::isNotEmpty) ?: preferred
      SeriesMetric.QUERY_TYPES -> item.name?.trim()?.takeIf(String::isNotEmpty) ?: preferred
      else -> preferred
    }
    return preferred to label
  }
}

