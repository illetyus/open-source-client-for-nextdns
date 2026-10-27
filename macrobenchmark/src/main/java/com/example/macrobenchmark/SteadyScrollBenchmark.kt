package com.example.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Startup, navigation, paging and fixture waits are deliberately outside measureBlock. */
@RunWith(AndroidJUnit4::class)
class SteadyScrollBenchmark {
  @get:Rule val rule = MacrobenchmarkRule()

  @Test fun setup() = scroll("setup")
  @Test fun security() = scroll("security")
  @Test fun privacy() = scroll("privacy")
  @Test fun parental() = scroll("parental")
  @Test fun denylist() = scroll("denylist")
  @Test fun allowlist() = scroll("allowlist")
  @Test fun analytics() = scroll("analytics")
  @Test fun analyticsMap() = scroll("analytics", anchor = "world_country_map")
  @Test fun analyticsSeries() = scroll("analytics", anchor = "analytics_series_line_chart")
  @Test fun analyticsDevices() = scroll("analytics", anchor = "analytics_devices_card", deviceCount = 500)
  @Test fun logs200() = scroll("logs", logRows = 200)
  @Test fun logs1000() = scroll("logs", logRows = 1_000)
  @Test fun settings() = scroll("settings")

  private fun scroll(screen: String, logRows: Int = 200, anchor: String? = null, deviceCount: Int = 1) {
    val args = InstrumentationRegistry.getArguments()
    val diagnostics = args.getString("scroll.diagnostics") == "true"
    val capture = args.getString("scroll.captureId") ?: "manual"
    val scenario = "$screen-${anchor ?: "top"}-$logRows-$deviceCount"
    val profileRequired = args.getString("scroll.profile") == "required"
    val compilation = if (profileRequired) {
      CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 3)
    } else if (args.getString("scroll.compilation") == "none") {
      CompilationMode.None()
    } else {
      CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3)
    }
    SyntheticNextDnsApi.start(logRows = logRows, deviceCount = deviceCount).use { fixture ->
      try {
        rule.measureRepeated(
          packageName = SyntheticNextDnsApi.TARGET_PACKAGE,
          metrics = listOf(FrameTimingMetric()),
          compilationMode = compilation,
          startupMode = StartupMode.WARM,
          iterations = 10,
          setupBlock = {
            startActivityAndWait(fixture.intentForFixture().apply {
              if (diagnostics) {
                putExtra("benchmark_diagnostics", true)
                putExtra("benchmark_capture", capture)
                putExtra("benchmark_scenario", scenario)
              }
            })
            check(device.wait(Until.hasObject(By.res("tab_setup")), 15_000))
            // Each retry reacquires Compose nodes; no retained UiObject2 across transitions.
            var reached = screen == "setup"
            repeat(16) {
              if (!reached) {
                try {
                  device.findObject(By.res("tab_$screen"))?.let { tab ->
                    if (tab.visibleBounds.width() > 0) {
                      tab.click()
                      reached = device.wait(Until.hasObject(By.res("screen_$screen")), 15_000)
                    }
                  }
                } catch (_: StaleObjectException) {
                  // Match the existing authenticated journey: reacquire a node
                  // replaced by Compose before choosing another tab/gesture.
                  return@repeat
                }
                if (!reached) device.swipe(
                  (device.displayWidth * .84).toInt(), (device.displayHeight * .20).toInt(),
                  (device.displayWidth * .18).toInt(), (device.displayHeight * .20).toInt(), 12
                )
              }
            }
            check(reached) { "Could not prepare $screen outside the measurement window" }
            if (screen == "analytics") {
              check(device.wait(Until.hasObject(By.res("analytics_ready")), 30_000)) {
                "Both analytics summary and series must finish before scrolling"
              }
            }
            if (screen == "allowlist" || screen == "denylist") {
              check(device.wait(Until.hasObject(By.text("rule-0.invalid")), 15_000)) {
                "A populated list is required; empty placeholders cannot certify scrolling"
              }
            }
            if (screen == "privacy") {
              check(device.wait(Until.hasObject(By.text("Benchmark list 0")), 15_000)) {
                "Profile privacy endpoints must populate active blocklists"
              }
            }
            if (screen == "logs") {
              check(device.wait(Until.hasObject(By.textContains("site-0.invalid")), 15_000))
              repeat(logRows / 100 - 1) { page ->
                var older = device.findObject(By.res("logs_load_older"))
                var swipes = 0
                while (older == null && swipes++ < 80) {
                  device.swipe(device.displayWidth / 2, (device.displayHeight * .82).toInt(),
                    device.displayWidth / 2, (device.displayHeight * .30).toInt(), 12)
                  older = device.wait(Until.findObject(By.res("logs_load_older")), 100)
                }
                checkNotNull(older) { "Missing synthetic log page ${page + 2}" }.click()
                check(device.wait(Until.hasObject(By.textContains("site-${(page + 1) * 100}.invalid")), 15_000))
              }
              // Restore the same scroll origin before every measured iteration.
              repeat(80) {
                if (!device.hasObject(By.textContains("site-0.invalid"))) {
                  device.swipe(device.displayWidth / 2, (device.displayHeight * .32).toInt(),
                    device.displayWidth / 2, (device.displayHeight * .82).toInt(), 8)
                }
              }
              check(device.hasObject(By.textContains("site-0.invalid")))
            }
            if (anchor != null) {
              var found = device.findObject(By.res(anchor))
              var swipes = 0
              while (found == null && swipes++ < 80) {
                device.swipe(device.displayWidth / 2, (device.displayHeight * .82).toInt(),
                  device.displayWidth / 2, (device.displayHeight * .32).toInt(), 12)
                found = device.wait(Until.findObject(By.res(anchor)), 100)
              }
              checkNotNull(found) { "Deep scroll target $anchor was not reached" }
              if (anchor == "world_country_map") {
                check(device.wait(Until.gone(By.res("world_map_loading")), 15_000))
                check(!device.hasObject(By.res("world_map_unavailable")))
              }
            }
            device.waitForIdle()
          }
        ) {
          // 8 slow drags = ~6 seconds of motion per iteration, plus both fling directions.
          // No lookups, waits, startup or data loading are included in this block.
          val x = device.displayWidth / 2
          val top = (device.displayHeight * .32).toInt()
          val bottom = (device.displayHeight * .82).toInt()
          repeat(4) {
            device.swipe(x, bottom, x, top, 150)
            device.swipe(x, top, x, bottom, 150)
          }
          repeat(2) {
            device.swipe(x, bottom, x, top, 8)
            device.swipe(x, top, x, bottom, 8)
          }
        }
      } finally {
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
          if (diagnostics) {
            // All file waiting is after measureRepeated, never inside motion.
            device.pressHome()
            val directory = "/sdcard/Android/media/${SyntheticNextDnsApi.TARGET_PACKAGE}/scroll-diagnostics/$capture/$scenario"
            check(Regex("[A-Za-z0-9_-]{1,96}").matches(capture))
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000L
            var complete = false
            while (!complete && android.os.SystemClock.uptimeMillis() < deadline) {
              val files = device.executeShellCommand("ls $directory")
                .lineSequence().map { it.trim() }.toList()
              // Diagnostic activity captures can include compilation warmups;
              // the core Perfetto gate separately validates measured iterations.
              // A .tmp file is never treated as committed.
              complete = files.count { it.endsWith(".json") } >= 10 && files.none { it.endsWith(".tmp") }
              if (!complete) android.os.SystemClock.sleep(100)
            }
            check(complete) { "Missing committed diagnostic frame files for $scenario" }
          }
        } finally {
          device.executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
        }
      }
    }
  }
}
