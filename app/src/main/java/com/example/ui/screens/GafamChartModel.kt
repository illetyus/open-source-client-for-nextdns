package com.example.ui.screens

import com.example.data.model.AnalyticsSummary
import java.util.Locale

internal enum class GafamCompany(val apiName: String) {
  GOOGLE("Google"),
  FACEBOOK("Facebook"),
  MICROSOFT("Microsoft"),
  APPLE("Apple"),
  AMAZON("Amazon"),
  OTHER("Other")
}

internal data class GafamSegment(
  val company: GafamCompany,
  val queries: Long,
  val sharePercent: Double
)

internal object GafamChartModel {
  private val companies = listOf(
    GafamCompany.GOOGLE,
    GafamCompany.FACEBOOK,
    GafamCompany.MICROSOFT,
    GafamCompany.APPLE,
    GafamCompany.AMAZON
  )

  fun from(analytics: AnalyticsSummary): List<GafamSegment> {
    val counts = GafamCompany.entries.associateWith { 0L }.toMutableMap()
    analytics.gafamMetrics.forEach { (name, metric) ->
      val company = companyForApiName(name)
      counts[company] = saturatingAdd(counts.getValue(company), metric.second.coerceAtLeast(0L))
    }
    val companyTotal = companies.fold(0L) { total, company ->
      saturatingAdd(total, counts.getValue(company))
    }
    val reportedTotal = analytics.totalQueries.coerceAtLeast(0L)
    val otherQueries = saturatingAdd(
      counts.getValue(GafamCompany.OTHER),
      (reportedTotal - saturatingAdd(companyTotal, counts.getValue(GafamCompany.OTHER))).coerceAtLeast(0L)
    )
    counts[GafamCompany.OTHER] = otherQueries
    val denominator = maxOf(reportedTotal, saturatingAdd(companyTotal, otherQueries))

    return GafamCompany.entries.map { company ->
      val queries = counts.getValue(company)
      val percent = if (denominator == 0L) 0.0 else queries.toDouble() / denominator.toDouble() * 100.0
      GafamSegment(company = company, queries = queries, sharePercent = percent.coerceIn(0.0, 100.0))
    }
  }

  private fun companyForApiName(name: String): GafamCompany = when (name.trim().lowercase(Locale.ROOT)) {
    "google" -> GafamCompany.GOOGLE
    "facebook", "meta", "meta (facebook)" -> GafamCompany.FACEBOOK
    "microsoft" -> GafamCompany.MICROSOFT
    "apple" -> GafamCompany.APPLE
    "amazon" -> GafamCompany.AMAZON
    else -> GafamCompany.OTHER
  }

  private fun saturatingAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
