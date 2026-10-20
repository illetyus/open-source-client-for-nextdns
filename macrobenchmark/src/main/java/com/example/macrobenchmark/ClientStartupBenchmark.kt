package com.example.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientStartupBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun coldStartupAndInitialScreenScroll() = benchmarkRule.measureRepeated(
    packageName = "com.aistudio.nextdns.mgrqvt",
    metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.COLD,
    setupBlock = { pressHome() }
  ) {
    startActivityAndWait()
    device.waitForIdle()
    val centerX = device.displayWidth / 2
    device.swipe(
      centerX,
      device.displayHeight * 4 / 5,
      centerX,
      device.displayHeight / 5,
      12
    )
  }
}
