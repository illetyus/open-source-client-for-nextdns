package com.example.performance

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.metrics.performance.JankStats
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in, local diagnostics only. Never packaged in a production build type. */
internal class BenchmarkFrameRecorder(activity: Activity, capture: String, private val scenario: String) {
  private val samples = BenchmarkFrameSamples()
  private val output = File(activity.externalMediaDirs.firstOrNull()
    ?: error("Benchmark diagnostics require an external media directory"), "scroll-diagnostics/$capture/$scenario")
  private val displayManager = activity.getSystemService(DisplayManager::class.java)
  private val displayId = activity.window.decorView.display?.displayId
  private data class DisplayState(val id: Int, val hz: Float)
  @Volatile private var displayState = DisplayState(-1, 0f)
  @Volatile private var thermalStatus = -1
  private val startedNanos = System.nanoTime()
  private val thermalObserver = if (Build.VERSION.SDK_INT >= 29) ThermalObserver(
    activity.getSystemService(PowerManager::class.java)
  ) { thermalStatus = it } else null
  private var closed = false
  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(id: Int) = Unit
    override fun onDisplayRemoved(id: Int) {
      if (id == displayId) displayState = DisplayState(-1, 0f)
    }
    override fun onDisplayChanged(id: Int) {
      if (id == displayId) readDisplay()
    }
  }
  private val stats: JankStats

  init {
    readDisplay()
    displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    stats = JankStats.createAndTrack(activity.window) { frame ->
      // FrameData is reused by the library. Copy primitives immediately; no
      // strings, view lookup, formatting, file I/O or telemetry on this callback.
      val mode = displayState
      samples.record(frame.frameStartNanos, frame.frameDurationUiNanos, frame.isJank,
        mode.id, mode.hz, thermalStatus)
    }
  }

  private fun readDisplay() {
    val mode = displayId?.let { displayManager.getDisplay(it)?.mode }
    displayState = DisplayState(mode?.modeId ?: -1, mode?.refreshRate ?: 0f)
  }

  /** Call on the UI thread, after the activity's last measured motion. */
  fun stopAndWrite() {
    if (closed) return
    closed = true
    stats.isTrackingEnabled = false
    displayManager.unregisterDisplayListener(displayListener)
    if (Build.VERSION.SDK_INT >= 29) thermalObserver?.close()
    val snapshot = samples.snapshot()
    val stoppedNanos = System.nanoTime()
    val writer = Executors.newSingleThreadExecutor { task ->
      Thread(task, "scroll-diagnostics-writer").apply { isDaemon = true }
    }
    writer.execute {
      runCatching {
        check(output.isDirectory || output.mkdirs()) { "Cannot create diagnostic output" }
        val rows = JSONArray()
        snapshot.startNanos.indices.forEach { i ->
          rows.put(JSONArray().put(snapshot.startNanos[i]).put(snapshot.uiNanos[i])
            .put(snapshot.jank[i]).put(snapshot.displayMode[i]).put(snapshot.refreshHz[i])
            .put(snapshot.thermalStatus[i]))
        }
        val json = JSONObject()
          .put("schema", 1).put("scenario", scenario)
          .put("scope", "activity_resume_to_stop_includes_preparation")
          .put("startedNanoTime", startedNanos).put("stoppedNanoTime", stoppedNanos)
          .put("presentationTimingIncluded", false).put("jankIsLibraryHeuristic", true)
          .put("uiDurationExcludesRenderThread", true)
          .put("refreshRateIsDisplayModeOnly", true).put("droppedFrames", snapshot.dropped)
          .put("columns", JSONArray(listOf("frameStartNanos", "frameDurationUiNanos", "isJank",
            "displayModeId", "displayRefreshHz", "thermalStatus")))
          .put("frames", rows)
        val temporary = File(output, "$startedNanos.tmp")
        temporary.bufferedWriter().use { it.write(json.toString()) }
        check(temporary.renameTo(File(output, "$startedNanos.json"))) { "Diagnostic commit failed" }
      }.onFailure { Log.e("ScrollDiagnostics", "Local diagnostic export failed", it) }
    }
    writer.shutdown()
  }

  @RequiresApi(29)
  private class ThermalObserver(private val manager: PowerManager, update: (Int) -> Unit) {
    private val listener = PowerManager.OnThermalStatusChangedListener { update(it) }
    init {
      update(manager.currentThermalStatus)
      manager.addThermalStatusListener(listener)
    }
    fun close() = manager.removeThermalStatusListener(listener)
  }
}
