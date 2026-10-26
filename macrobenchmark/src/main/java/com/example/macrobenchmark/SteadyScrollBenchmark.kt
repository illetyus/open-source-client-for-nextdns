package com.example.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
  @Test fun logs200() = scroll("logs", logRows = 200)
  @Test fun logs1000() = scroll("logs", logRows = 1_000)
  @Test fun settings() = scroll("settings")

  private fun scroll(screen: String, logRows: Int = 200) {
    val args = InstrumentationRegistry.getArguments()
    val profileRequired = args.getString("scroll.profile") == "required"
    val compilation = if (profileRequired) {
      CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 3)
    } else if (args.getString("scroll.compilation") == "none") {
      CompilationMode.None()
    } else {
      CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3)
    }
    SyntheticNextDnsApi.start(logRows = logRows).use { fixture ->
      try {
        rule.measureRepeated(
          packageName = SyntheticNextDnsApi.TARGET_PACKAGE,
          metrics = listOf(FrameTimingMetric()),
          compilationMode = compilation,
          startupMode = StartupMode.WARM,
          iterations = 10,
          setupBlock = {
            startActivityAndWait(fixture.intentForFixture())
            check(device.wait(Until.hasObject(By.res("tab_setup")), 15_000))
            // Each retry reacquires Compose nodes; no retained UiObject2 across transitions.
            var reached = screen == "setup"
            repeat(16) {
              if (!reached) {
                device.findObject(By.res("tab_$screen"))?.let { tab ->
                  if (tab.visibleBounds.width() > 0) {
                    tab.click()
                    reached = device.wait(Until.hasObject(By.res("screen_$screen")), 15_000)
                  }
                }
                if (!reached) device.swipe(
                  (device.displayWidth * .84).toInt(), (device.displayHeight * .20).toInt(),
                  (device.displayWidth * .18).toInt(), (device.displayHeight * .20).toInt(), 12
                )
              }
            }
            check(reached) { "Could not prepare $screen outside the measurement window" }
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
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
          .executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
      }
    }
  }
}
