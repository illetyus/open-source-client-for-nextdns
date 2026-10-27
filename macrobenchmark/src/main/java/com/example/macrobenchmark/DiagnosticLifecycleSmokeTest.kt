package com.example.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Functional lifecycle/export check only; safe on a disposable CI emulator. */
@RunWith(AndroidJUnit4::class)
class DiagnosticLifecycleSmokeTest {
  @Test fun stoppedFixtureCommitsDetachedFrameDiagnostics() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val capture = "smoke-${SystemClock.elapsedRealtimeNanos()}"
    val directory = "/sdcard/Android/media/${SyntheticNextDnsApi.TARGET_PACKAGE}/scroll-diagnostics/$capture/setup-smoke"
    SyntheticNextDnsApi.start().use { fixture ->
      try {
        device.executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
        instrumentation.context.startActivity(fixture.intentForFixture()
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
          .putExtra("benchmark_diagnostics", true)
          .putExtra("benchmark_capture", capture)
          .putExtra("benchmark_scenario", "setup-smoke"))
        assertTrue("Real fixture UI must load", device.wait(Until.hasObject(By.res("screen_setup")), 30_000))
        val x = device.displayWidth / 2
        val top = (device.displayHeight * .35).toInt()
        val bottom = (device.displayHeight * .80).toInt()
        repeat(2) {
          device.swipe(x, bottom, x, top, 150)
          device.swipe(x, top, x, bottom, 150)
        }
        device.pressHome()
        val deadline = SystemClock.uptimeMillis() + 15_000L
        var filename: String? = null
        while (filename == null && SystemClock.uptimeMillis() < deadline) {
          filename = device.executeShellCommand("ls $directory").lineSequence()
            .map { it.trim() }.firstOrNull { Regex("[0-9]+\\.json").matches(it) }
          if (filename == null) SystemClock.sleep(100)
        }
        assertNotNull("onStop must commit a complete frame file", filename)
        val json = JSONObject(device.executeShellCommand("cat $directory/$filename"))
        assertEquals("setup-smoke", json.getString("scenario"))
        assertFalse(json.getBoolean("presentationTimingIncluded"))
        assertTrue(json.getBoolean("jankIsLibraryHeuristic"))
        assertTrue(json.getBoolean("uiDurationExcludesRenderThread"))
        assertEquals(0L, json.getLong("droppedFrames"))
        assertTrue(json.getLong("stoppedNanoTime") >= json.getLong("startedNanoTime"))
        val frames = json.getJSONArray("frames")
        assertTrue("Real Android frame callbacks must be recorded", frames.length() > 0)
        repeat(frames.length()) { index ->
          val row = frames.getJSONArray(index)
          assertEquals(6, row.length())
          assertTrue(row.getLong(0) > 0L)
          assertTrue(row.getLong(1) >= 0L)
          assertTrue(row.getDouble(4).isFinite() && row.getDouble(4) > 0.0)
        }
      } finally {
        device.executeShellCommand("am force-stop ${SyntheticNextDnsApi.TARGET_PACKAGE}")
      }
    }
  }
}
