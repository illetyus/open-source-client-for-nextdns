package com.example.performance

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BenchmarkFrameSamplesTest {
  @Test fun framePrimitivesSurviveTheReusableCallbackObject() {
    val samples = BenchmarkFrameSamples(capacity = 2)
    samples.record(123, 456, true, 3, 120f, 2)
    val snapshot = samples.snapshot()
    assertArrayEquals(longArrayOf(123), snapshot.startNanos)
    assertArrayEquals(longArrayOf(456), snapshot.uiNanos)
    assertArrayEquals(booleanArrayOf(true), snapshot.jank)
    assertArrayEquals(intArrayOf(3), snapshot.displayMode)
    assertArrayEquals(floatArrayOf(120f), snapshot.refreshHz, 0f)
    assertArrayEquals(intArrayOf(2), snapshot.thermalStatus)
    assertEquals(0L, snapshot.dropped)
  }

  @Test fun overflowIsReportedWithoutErasingUnfavorableFrames() {
    val samples = BenchmarkFrameSamples(capacity = 1)
    samples.record(10, 100_000_000, true, 1, 60f, 3)
    samples.record(20, 100, false, 1, 60f, 3)
    val snapshot = samples.snapshot()
    assertArrayEquals(longArrayOf(100_000_000), snapshot.uiNanos)
    assertEquals(1L, snapshot.dropped)
  }

  @Test fun snapshotDoesNotMutateAsNewFramesArrive() {
    val samples = BenchmarkFrameSamples(capacity = 3)
    samples.record(1, 2, false, 1, 60f, 0)
    val earlier = samples.snapshot()
    samples.record(3, 4, true, 2, 120f, 1)
    assertArrayEquals(longArrayOf(1), earlier.startNanos)
    earlier.startNanos[0] = 999
    assertArrayEquals(longArrayOf(1, 3), samples.snapshot().startNanos)
  }

  @Test fun concurrentCallbacksKeepAnExactBoundAndOverflowCount() {
    val samples = BenchmarkFrameSamples(capacity = 100)
    val workers = Executors.newFixedThreadPool(3)
    try {
      repeat(3) { worker -> workers.submit {
        repeat(100) { samples.record((worker * 100 + it).toLong(), 7, false, 1, 60f, 0) }
      } }
      workers.shutdown()
      assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
      val snapshot = samples.snapshot()
      assertEquals(100, snapshot.startNanos.size)
      assertEquals(100, snapshot.startNanos.toSet().size)
      assertEquals(200L, snapshot.dropped)
    } finally {
      workers.shutdownNow()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun zeroCapacityIsRejected() {
    BenchmarkFrameSamples(capacity = 0)
  }
}
