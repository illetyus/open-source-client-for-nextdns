package com.example.ui.screens

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestLifecycleTest {
  @Test
  fun backgroundStopCancelsTheActiveSpeedTestJob() {
    val job = Job()
    val owner = object : LifecycleOwner {
      override val lifecycle: Lifecycle get() = error("The observer only needs the lifecycle event")
    }

    speedTestLifecycleObserver { job }.onStateChanged(owner, Lifecycle.Event.ON_STOP)

    assertFalse(job.isActive)
  }

  @Test
  fun temporaryPauseDoesNotCancelAStillVisibleSpeedTest() {
    val job = Job()
    val owner = object : LifecycleOwner {
      override val lifecycle: Lifecycle get() = error("The observer only needs the lifecycle event")
    }

    speedTestLifecycleObserver { job }.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)

    assertTrue(job.isActive)
    job.cancel()
  }
}
