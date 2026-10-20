package com.example

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.screens.loadWorldMapGeometry
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android StrictMode coverage for the world-map asset load. The negative probe
 * proves this policy detects main-thread disk and network work before the
 * production path is checked for violations.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class WorldMapStrictModeTest {
  @Test fun strictModePolicyDetectsMainThreadDiskAndNetworkFixtures() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val probeFile = File(context.cacheDir, "strict-mode-disk-probe.txt").apply { writeText("probe") }
    val violations = CopyOnWriteArrayList<String>()
    val diskReadReported = CountDownLatch(1)
    val networkReported = CountDownLatch(1)
    val previousPolicy = AtomicReference<StrictMode.ThreadPolicy>()

    instrumentation.runOnMainSync {
      previousPolicy.set(StrictMode.getThreadPolicy())
      StrictMode.setThreadPolicy(policy(violations, diskReadReported, networkReported))
      try {
        probeFile.inputStream().use { it.read() }
        runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 1), 250) } }
      } finally {
        StrictMode.setThreadPolicy(previousPolicy.get())
      }
    }

    assertTrue("StrictMode disk-read callback was not delivered", diskReadReported.await(2, TimeUnit.SECONDS))
    assertTrue("StrictMode network callback was not delivered", networkReported.await(2, TimeUnit.SECONDS))
    assertTrue("The policy must detect disk reads: $violations", violations.any { it.contains("DiskRead") })
    assertTrue("The policy must detect network work: $violations", violations.any { it.contains("Network") })
    assertTrue(probeFile.delete() || !probeFile.exists())
  }

  @Test fun bundledWorldMapLoadsWithoutMainThreadDiskOrNetworkViolations() = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext.applicationContext
    val violations = CopyOnWriteArrayList<String>()
    val previousPolicy = AtomicReference<StrictMode.ThreadPolicy>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val result = CompletableDeferred<Int>()

    instrumentation.runOnMainSync {
      previousPolicy.set(StrictMode.getThreadPolicy())
      StrictMode.setThreadPolicy(policy(violations))
      scope.launch {
        try {
          result.complete(loadWorldMapGeometry(context).countries.size)
        } catch (failure: Throwable) {
          result.completeExceptionally(failure)
        }
      }
    }

    try {
      assertEquals(175, withTimeout(15_000) { result.await() })
      // StrictMode measures main-loop violations and delivers listener callbacks on a later loop turn.
      instrumentation.runOnMainSync { }
      assertTrue("World-map loading caused main-thread I/O: $violations", violations.isEmpty())
    } finally {
      instrumentation.runOnMainSync {
        StrictMode.setThreadPolicy(previousPolicy.get())
        scope.cancel()
      }
    }
  }

  private fun policy(
    violations: MutableList<String>,
    diskReadReported: CountDownLatch? = null,
    networkReported: CountDownLatch? = null
  ): StrictMode.ThreadPolicy =
    StrictMode.ThreadPolicy.Builder()
      .detectDiskReads()
      .detectDiskWrites()
      .detectNetwork()
      .penaltyListener(Executor { command -> command.run() }) { violation ->
        val type = violation.javaClass.simpleName
        violations += type
        if (type.contains("DiskRead")) diskReadReported?.countDown()
        if (type.contains("Network")) networkReported?.countDown()
      }
      .build()
}

