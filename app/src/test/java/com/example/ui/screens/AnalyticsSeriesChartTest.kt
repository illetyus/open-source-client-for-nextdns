package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.audit.AuditComposeTestRule
import com.example.data.model.AnalyticsSeriesState
import com.example.data.model.SeriesMetric
import com.example.data.model.SeriesPoint
import com.example.data.model.SeriesRow
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.theme.AppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class AnalyticsSeriesChartTest {
  @get:Rule val rule = AuditComposeTestRule()

  @Test fun metricSelectorCanChooseOnlyDocumentedDestinationSeries() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
    var selected = SeriesMetric.STATUS
    rule.setContent {
      AppTheme {
        AnalyticsSeriesChart(
          state = AnalyticsSeriesState(), selectedMetric = selected, domainStatus = null, rootGrouped = false,
          onMetricSelected = { selected = it }, onDomainStatusSelected = {}, onRootGroupedChanged = {}
        )
      }
    }

    rule.onNodeWithTag("analytics_series_metric").performClick()
    rule.onNodeWithTag("analytics_series_metric_countries").performClick()
    rule.runOnIdle { assertEquals(SeriesMetric.COUNTRIES, selected) }
  }

  @Test fun chartAndTableKeepZeroDistinctFromMissingBuckets() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
    val row = SeriesRow(
      key = "blocked",
      label = "Blocked",
      points = listOf(
        SeriesPoint(Instant.parse("2026-10-20T00:00:00Z"), null),
        SeriesPoint(Instant.parse("2026-10-20T01:00:00Z"), 0L)
      )
    )
    rule.setContent {
      AppTheme {
        AnalyticsSeriesChart(
          state = AnalyticsSeriesState(query = null, rows = listOf(row), intervalSeconds = 3_600L),
          selectedMetric = SeriesMetric.STATUS, domainStatus = null, rootGrouped = false,
          onMetricSelected = {}, onDomainStatusSelected = {}, onRootGroupedChanged = {}
        )
      }
    }

    rule.onNodeWithTag("analytics_series_line_chart").assertExists()
    rule.onNodeWithText(AppStrings.get(R.string.analytics_series_no_value)).assertIsDisplayed()
    rule.onNodeWithTag("analytics_series_selected_point").assertExists()
    rule.onNodeWithTag("analytics_series_rows").assertExists()
  }

  @Test fun domainSeriesExposeOnlyDocumentedStatusAndRootControls() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
    var selectedStatus: String? = null
    var rootGrouped = false
    rule.setContent {
      AppTheme {
        AnalyticsSeriesChart(
          state = AnalyticsSeriesState(), selectedMetric = SeriesMetric.DOMAINS,
          domainStatus = selectedStatus, rootGrouped = rootGrouped,
          onMetricSelected = {}, onDomainStatusSelected = { selectedStatus = it },
          onRootGroupedChanged = { rootGrouped = it }
        )
      }
    }

    rule.onNodeWithTag("analytics_series_domain_status_2").performClick()
    rule.runOnIdle { assertEquals("blocked", selectedStatus) }
    rule.onNodeWithTag("analytics_series_root_toggle").performClick()
    rule.runOnIdle { assertEquals(true, rootGrouped) }
  }
}

