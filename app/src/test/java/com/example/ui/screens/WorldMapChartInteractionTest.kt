package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.data.model.CountryTrafficMetric
import com.example.i18n.LocalePreferences
import com.example.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h900dp-mdpi", application = NextDnsApp::class)
class WorldMapChartInteractionTest {
  @get:Rule val rule = createComposeRule()

  @Test fun tappingAColoredCountryShowsItsNameAndTrafficShare() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
    rule.setContent {
      AppTheme {
        WorldMapChart(
          countryData = listOf(
            CountryTrafficMetric("TR", 28, 40.0),
            CountryTrafficMetric("US", 42, 60.0, listOf("dns.example", "mail.example")),
          ),
          modifier = Modifier.width(320.dp)
        )
      }
    }

    rule.waitUntil(30_000) {
      rule.onAllNodesWithTag("world_map_loading").fetchSemanticsNodes().isEmpty() &&
        rule.onAllNodesWithTag("world_map_unavailable").fetchSemanticsNodes().isEmpty()
    }
    rule.onNodeWithTag("world_country_map").assertExists().performTouchInput {
      click(Offset(74f, 60f))
    }

    rule.onNodeWithTag("world_map_selected_country").assertTextContains("United States")
    rule.onNodeWithTag("world_map_selected_share").assertTextContains("60.00%", substring = true)
    rule.onNodeWithText("42 queries").assertIsDisplayed()
    rule.onNodeWithText("dns.example").assertIsDisplayed()
  }

  @Test fun bundledMapGeometryContainsMajorCountriesAndSelectsByLocation() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val geometry = context.assets.open("maps/world-countries.tsv").bufferedReader().use {
      parseWorldMapAsset(it.readText())
    }

    assertEquals(175, geometry.countries.size)
    assertEquals("US", geometry.countryAt(0.23f, 0.29f))
  }

  @Test fun accessibleCountrySelectorIncludesCountriesWithoutTrafficData() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
    rule.setContent {
      AppTheme {
        WorldMapChart(
          countryData = listOf(CountryTrafficMetric("US", 42, 100.0)),
          modifier = Modifier.width(320.dp)
        )
      }
    }

    rule.waitUntil(30_000) {
      rule.onAllNodesWithTag("world_map_loading").fetchSemanticsNodes().isEmpty() &&
        rule.onAllNodesWithTag("world_map_unavailable").fetchSemanticsNodes().isEmpty()
    }
    rule.onNodeWithTag("world_map_country_selector").performClick()
    rule.onNodeWithTag("world_map_country_option_ZW").assertExists()
    rule.onNodeWithTag("world_map_country_option_AF").assertExists().performClick()
    rule.onNodeWithTag("world_map_selected_country").assertTextContains("Afghanistan")
    rule.onNodeWithTag("world_map_selected_queries").assertTextContains("No traffic data", substring = true)
  }
}
