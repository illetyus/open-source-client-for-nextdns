package com.example.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientStartupBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun coldAuthenticatedFixtureStartupAndInitialScreenScroll() {
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
        startActivityAndWait(fixture.intentForFixture())
        check(device.wait(Until.hasObject(By.res("tab_setup")), 15_000)) {
          "Benchmark fixture Home screen did not render"
        }
        device.swipe(
          device.displayWidth / 2,
          (device.displayHeight * 0.82f).toInt(),
          device.displayWidth / 2,
          (device.displayHeight * 0.28f).toInt(),
          18
        )
      }
    } finally {
      try {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
          .executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
      } finally {
        fixture.close()
      }
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

  private companion object {
    const val WARMUP_ITERATIONS = 2
    const val MEASURED_ITERATIONS = 10
  }
}
