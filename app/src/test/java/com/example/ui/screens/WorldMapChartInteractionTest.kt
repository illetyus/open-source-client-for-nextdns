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
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathFillType
import kotlinx.coroutines.runBlocking
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

  @Test fun geometryIsReusedAcrossScreenVisits() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    assertSame(loadWorldMapGeometry(context), loadWorldMapGeometry(context))
  }

  @Test fun renderPathsReuseViewportAndRetainSeamCopiesAndHoleFill() {
    val outer = listOf(WorldMapPoint(.99f, .2f), WorldMapPoint(1.02f, .2f),
      WorldMapPoint(1.02f, .4f), WorldMapPoint(.99f, .2f))
    val hole = listOf(WorldMapPoint(.995f, .25f), WorldMapPoint(1.005f, .25f),
      WorldMapPoint(1f, .30f), WorldMapPoint(.995f, .25f))
    val geometry = WorldMapGeometry(listOf(WorldCountryOutline("US", listOf(WorldMapPolygon(listOf(outer, hole))))))
    val cache = WorldMapRenderCache(geometry)
    val viewport = Rect(0f, 0f, 1_000f, 500f)
    val first = cache.paths(viewport, 1f)
    assertSame(first, cache.paths(viewport, 1f))
    assertEquals("Both visible antimeridian copies must remain", 2, first.single().paths.size)
    assertTrue(first.single().paths.all { it.fillType == PathFillType.EvenOdd })
    assertNotSame(first, cache.paths(Rect(0f, 0f, 500f, 250f), 1f))
  }

  @Test fun cullingIncludesTheOutlineOutsideTheViewport() {
    val ring = listOf(WorldMapPoint(1.001f, .2f), WorldMapPoint(1.01f, .2f),
      WorldMapPoint(1.01f, .4f), WorldMapPoint(1.001f, .2f))
    val cache = WorldMapRenderCache(WorldMapGeometry(listOf(
      WorldCountryOutline("US", listOf(WorldMapPolygon(listOf(ring)))))))
    val viewport = Rect(0f, 0f, 1_000f, 500f)
    assertEquals(1, cache.paths(viewport, 0f).single().paths.size)
    assertEquals(2, cache.paths(viewport, 2f).single().paths.size)
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
