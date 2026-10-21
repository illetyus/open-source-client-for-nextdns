package com.example.audit

import com.example.NextDnsApp
import com.example.data.model.AnalyticsSeriesQuery
import com.example.data.model.SeriesMetric
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class AnalyticsSeriesRepositoryTest : AuditFixture() {
  @get:Rule val mainDispatcherRule = AuditMainDispatcherRule()

  @Test fun analyticsSeriesRequestsOnlyTheSelectedMetricAndPreservesTheApiTimeAxis() = runBlocking {
    val query = AnalyticsSeriesQuery(
      profileId = "aaaaaa", deviceId = "__UNIDENTIFIED__",
      fromInclusive = Instant.parse("2026-10-20T00:00:00Z"),
      toExclusive = Instant.parse("2026-10-20T02:00:00Z"),
      metric = SeriesMetric.QUERY_TYPES, intervalSeconds = 3_600L
    )
    response = {
      json("""{"data":[{"type":1,"name":"A","queries":[4,9]}],"meta":{"series":{"times":["2026-10-20T00:00:00Z","2026-10-20T01:00:00Z"],"interval":3600}}}""")
    }
    assertTrue(repository.fetchAnalyticsSeries(query))
    assertEquals(1, requests.size)
    assertTrue(requests.single().startsWith("GET /profiles/aaaaaa/analytics/queryTypes;series?"))
    assertTrue(requests.single().contains("device=__UNIDENTIFIED__"))
    assertTrue(requests.single().contains("interval=3600"))
    assertEquals(listOf(4L, 9L), repository.analyticsSeries.value.rows.single().points.map { it.queries })
    assertEquals(Instant.parse("2026-10-20T01:00:00Z"), repository.analyticsSeries.value.rows.single().points.last().at)
  }

  @Test fun obsoleteProfileSeriesCannotOverwriteCurrentState() = runBlocking {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    response = {
      started.countDown()
      check(release.await(10, TimeUnit.SECONDS))
      json("""{"data":[{"status":"blocked","queries":[19]}],"meta":{"series":{"times":["2026-10-20T00:00:00Z"],"interval":3600}}}""")
    }
    val request = async {
      repository.fetchAnalyticsSeries(
        AnalyticsSeriesQuery(
          profileId = "aaaaaa", deviceId = null,
          fromInclusive = Instant.parse("2026-10-20T00:00:00Z"),
          toExclusive = Instant.parse("2026-10-20T01:00:00Z"),
          metric = SeriesMetric.STATUS, intervalSeconds = 3_600L
        )
      )
    }
    assertTrue(started.await(5, TimeUnit.SECONDS))
    repository.setActiveProfile("bbbbbb")
    release.countDown()
    try {
      request.await()
    } catch (_: CancellationException) {
      // If the request completes, the final session check still must discard it.
    }
    assertNull(repository.analyticsSeries.value.query)
    assertTrue(repository.analyticsSeries.value.rows.isEmpty())
  }
}
