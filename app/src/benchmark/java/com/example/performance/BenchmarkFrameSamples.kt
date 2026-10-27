package com.example.performance

/** Bounded diagnostic storage; no frame callback performs file I/O or allocates a row. */
internal class BenchmarkFrameSamples(capacity: Int = 30_000) {
  init { require(capacity > 0) }

  private val startNanos = LongArray(capacity)
  private val uiNanos = LongArray(capacity)
  private val jank = BooleanArray(capacity)
  private val displayMode = IntArray(capacity)
  private val refreshHz = FloatArray(capacity)
  private val thermalStatus = IntArray(capacity)
  private var size = 0
  private var dropped = 0L

  @Synchronized
  fun record(start: Long, ui: Long, isJank: Boolean, mode: Int, hz: Float, thermal: Int) {
    if (size == startNanos.size) {
      dropped++
      return
    }
    val index = size++
    startNanos[index] = start
    uiNanos[index] = ui
    jank[index] = isJank
    displayMode[index] = mode
    refreshHz[index] = hz
    thermalStatus[index] = thermal
  }

  @Synchronized
  fun snapshot() = BenchmarkFrameSnapshot(
    startNanos.copyOf(size), uiNanos.copyOf(size), jank.copyOf(size),
    displayMode.copyOf(size), refreshHz.copyOf(size), thermalStatus.copyOf(size), dropped
  )
}

/** Detached primitive copies: JankStats reuses its FrameData callback instance. */
internal data class BenchmarkFrameSnapshot(
  val startNanos: LongArray,
  val uiNanos: LongArray,
  val jank: BooleanArray,
  val displayMode: IntArray,
  val refreshHz: FloatArray,
  val thermalStatus: IntArray,
  val dropped: Long
)
