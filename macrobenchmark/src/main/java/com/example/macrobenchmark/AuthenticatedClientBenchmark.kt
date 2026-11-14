package com.example.macrobenchmark

import android.graphics.Rect
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatedClientBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun logsHistoryLoadsAndScrollsTwoHundredSyntheticRows() {
    val fixture = SyntheticNextDnsApi.start()
    try {
      warmUpFixtureTwice(fixture)
      benchmarkRule.measureRepeated(
        packageName = SyntheticNextDnsApi.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = MEASURED_ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
      ) {
        launchFixture(fixture)
        tapTab("tab_logs", "logs_history_range_button")
        val loadOlder = scrollUntilFound(By.res("logs_load_older"), "older-log pagination control")
        loadOlder.click()
        scrollUntilFound(By.textContains("site-199.invalid"), "last row from the second log page")
      }
    } finally {
      try {
        stopFixtureApp()
      } finally {
        fixture.close()
      }
    }
  }

  @Test
  fun analyticsSeriesAndInteractiveCountryMapRender() {
    val fixture = SyntheticNextDnsApi.start()
    try {
      warmUpFixtureTwice(fixture)
      benchmarkRule.measureRepeated(
        packageName = SyntheticNextDnsApi.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = MEASURED_ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
      ) {
        launchFixture(fixture)
        tapTab("tab_analytics", "analytics_device_filter")
        val map = scrollUntilFound(By.res("world_country_map"), "interactive country map")
        map.click()
        scrollUntilFound(By.res("analytics_series_line_chart"), "synthetic analytics series")
        repeat(3) { swipeUpThroughList() }
      }
    } finally {
      try {
        stopFixtureApp()
      } finally {
        fixture.close()
      }
    }
  }

  private fun androidx.benchmark.macro.MacrobenchmarkScope.launchFixture(fixture: SyntheticNextDnsApi) {
    startActivityAndWait(fixture.intentForFixture())
    check(device.wait(Until.hasObject(By.res("tab_setup")), 15_000)) {
      "Benchmark fixture Home screen did not render"
    }
  }

  private fun warmUpFixtureTwice(fixture: SyntheticNextDnsApi) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    repeat(WARMUP_ITERATIONS) {
      device.executeShellCommand(fixture.shellLaunchCommand())
      check(device.wait(Until.hasObject(By.res("tab_setup")), 15_000)) {
        "Benchmark fixture warmup did not reach Home"
      }
      device.pressHome()
      device.executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
    }
  }

  private fun stopFixtureApp() {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
      .executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
  }

  private fun androidx.benchmark.macro.MacrobenchmarkScope.tapTab(tag: String, destinationTag: String) {
    repeat(12) {
      val tab = device.findObject(By.res(tag))
      val bounds = tab?.visibleBoundsOrNull()
      if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
        try {
          tab.click()
          check(device.wait(Until.hasObject(By.res(destinationTag)), 15_000)) {
            "Navigation to $tag did not render $destinationTag"
          }
          return
        } catch (_: StaleObjectException) {
          // Compose may replace the accessibility node between bounds lookup and click.
        }
      }
      val anchorBounds = listOf("tab_setup", "tab_logs", "tab_analytics")
        .asSequence()
        .mapNotNull { device.findObject(By.res(it))?.visibleBoundsOrNull() }
        .firstOrNull { it.height() > 0 }
      val y = anchorBounds?.centerY() ?: (device.displayHeight * 0.20f).toInt()
      device.swipe(
        (device.displayWidth * 0.84f).toInt(), y,
        (device.displayWidth * 0.18f).toInt(), y, 12
      )
    }
    error("Navigation tab $tag was not reachable")
  }

  private fun UiObject2.visibleBoundsOrNull(): Rect? =
    try {
      visibleBounds
    } catch (_: StaleObjectException) {
      null
    }

  private fun androidx.benchmark.macro.MacrobenchmarkScope.swipeUpThroughList() {
    val centerX = device.displayWidth / 2
    device.swipe(
      centerX,
      (device.displayHeight * 0.82f).toInt(),
      centerX,
      (device.displayHeight * 0.28f).toInt(),
      18
    )
  }

  private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollUntilFound(
    selector: BySelector,
    description: String
  ): UiObject2 {
    repeat(MAX_SCROLL_SWIPES) {
      val item = device.wait(Until.findObject(selector), 100)
      if (item != null && item.visibleBounds.width() > 0 && item.visibleBounds.height() > 0) return item
      swipeUpThroughList()
    }
    error("Could not find $description after $MAX_SCROLL_SWIPES scrolls")
  }

  private companion object {
    const val WARMUP_ITERATIONS = 2
    const val MEASURED_ITERATIONS = 10
    const val MAX_SCROLL_SWIPES = 80
  }
}
