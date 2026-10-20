package com.example.ui.screens

import com.example.data.model.AnalyticsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GafamChartModelTest {
  @Test fun chartAndLegendUseOneCountBasedSixSegmentModel() {
    val result = GafamChartModel.from(
      AnalyticsSummary(
        totalQueries = 100,
        gafamMetrics = mapOf(
          "Google" to (90.0 to 10L),
          "Facebook" to (1.0 to 20L),
          "Microsoft" to (2.0 to 30L),
          "Apple" to (3.0 to 20L),
          "Amazon" to (4.0 to 10L)
        )
      )
    )

    assertEquals(
      listOf(GafamCompany.GOOGLE, GafamCompany.FACEBOOK, GafamCompany.MICROSOFT,
        GafamCompany.APPLE, GafamCompany.AMAZON, GafamCompany.OTHER),
      result.map { it.company }
    )
    assertEquals(listOf(10L, 20L, 30L, 20L, 10L, 10L), result.map { it.queries })
    assertEquals(listOf(10.0, 20.0, 30.0, 20.0, 10.0, 10.0), result.map { it.sharePercent })
    assertEquals(100.0, result.sumOf { it.sharePercent }, 0.0001)
  }

  @Test fun missingCompaniesBecomeZeroAndAllUnattributedTrafficIsOther() {
    val result = GafamChartModel.from(AnalyticsSummary(totalQueries = 15))

    assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 15L), result.map { it.queries })
    assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 100.0), result.map { it.sharePercent })
  }

  @Test fun documentedLowercaseApiCompanyNamesAndExplicitOthersAreRecognized() {
    val result = GafamChartModel.from(
      AnalyticsSummary(
        totalQueries = 100,
        gafamMetrics = mapOf(
          "google" to (0.0 to 25L),
          "facebook" to (0.0 to 15L),
          "microsoft" to (0.0 to 10L),
          "apple" to (0.0 to 20L),
          "amazon" to (0.0 to 20L),
          "others" to (0.0 to 10L)
        )
      )
    )

    assertEquals(listOf(25L, 15L, 10L, 20L, 20L, 10L), result.map { it.queries })
    assertEquals(100.0, result.sumOf { it.sharePercent }, 0.0001)
  }

  @Test fun overReportedCompanyCountsAreNormalizedWithoutNegativeOther() {
    val result = GafamChartModel.from(
      AnalyticsSummary(
        totalQueries = 100,
        gafamMetrics = mapOf("Google" to (10.0 to 80L), "Apple" to (10.0 to 80L))
      )
    )

    assertEquals(0L, result.last().queries)
    assertEquals(50.0, result.first().sharePercent, 0.0001)
    assertEquals(50.0, result[3].sharePercent, 0.0001)
    assertEquals(100.0, result.sumOf { it.sharePercent }, 0.0001)
    assertTrue(result.all { it.sharePercent.isFinite() && it.sharePercent in 0.0..100.0 })
  }

  @Test fun zeroOrNegativeTotalsAndCountsNeverProduceInvalidShares() {
    val result = GafamChartModel.from(
      AnalyticsSummary(
        totalQueries = -1,
        gafamMetrics = mapOf("Google" to (100.0 to -9L), "Amazon" to (50.0 to 0L))
      )
    )

    assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L), result.map { it.queries })
    assertTrue(result.all { it.sharePercent == 0.0 })
  }

  @Test fun companyKeysAreMatchedCaseInsensitivelyAndApiPercentagesDoNotOverrideCounts() {
    val result = GafamChartModel.from(
      AnalyticsSummary(
        totalQueries = 4,
        gafamMetrics = mapOf("gOoGlE" to (99.0 to 1L), "AMAZON" to (0.0 to 1L))
      )
    )

    assertEquals(25.0, result.first().sharePercent, 0.0001)
    assertEquals(25.0, result[4].sharePercent, 0.0001)
    assertEquals(2L, result.last().queries)
  }
}
