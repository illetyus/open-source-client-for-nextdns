package com.example.data.api

import com.example.data.model.AnalyticsSeriesQuery
import com.example.data.model.SeriesMetric
import java.time.Instant
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AnalyticsSeriesContractTest {
  @Test fun officialQueryTypesSeriesPreservesTimesAndCounts() {
    val query = query()
    val rows = AnalyticsSeriesMapper.parse(
      query,
      ApiSeriesMeta(
        times = listOf("2026-10-20T00:00:00Z", "2026-10-20T01:00:00Z"),
        interval = 3_600L
      ),
      listOf(AnalyticsSeriesDto(type = 1, name = "A", queries = listOf(4L, 9L)))
    )

    assertEquals("1", rows.single().key)
    assertEquals("A", rows.single().label)
    assertEquals(Instant.parse("2026-10-20T00:00:00Z"), rows.single().points.first().at)
    assertEquals(4L, rows.single().points.first().queries)
    assertEquals(9L, rows.single().points.last().queries)
  }

  @Test fun missingBucketIsNotZero() {
    val rows = AnalyticsSeriesMapper.parse(
      query(),
      ApiSeriesMeta(times = listOf("2026-10-20T00:00:00Z", "2026-10-20T01:00:00Z"), interval = 3_600L),
      listOf(AnalyticsSeriesDto(status = "blocked", queries = listOf(0L, null)))
    )

    assertEquals(0L, rows.single().points[0].queries)
    assertNull(rows.single().points[1].queries)
  }

  @Test fun groupedDomainSeriesUsesTheRootDomainForKeyAndLabel() {
    val rows = AnalyticsSeriesMapper.parse(
      AnalyticsSeriesQuery(
        profileId = "abcdef",
        deviceId = null,
        fromInclusive = Instant.parse("2026-10-20T00:00:00Z"),
        toExclusive = Instant.parse("2026-10-20T01:00:00Z"),
        metric = SeriesMetric.DOMAINS,
        intervalSeconds = 3_600L,
        root = true
      ),
      ApiSeriesMeta(times = listOf("2026-10-20T00:00:00Z"), interval = 3_600L),
      listOf(AnalyticsSeriesDto(domain = "sub.example.test", root = "example.test", queries = listOf(3L)))
    )

    assertEquals("example.test", rows.single().key)
    assertEquals("example.test", rows.single().label)
  }

  @Test fun mismatchedSeriesLengthsAreRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      AnalyticsSeriesMapper.parse(
        query(),
        ApiSeriesMeta(times = listOf("2026-10-20T00:00:00Z", "2026-10-20T01:00:00Z"), interval = 3_600L),
        listOf(AnalyticsSeriesDto(status = "blocked", queries = listOf(7L)))
      )
    }
  }

  @Test fun negativeAndOverflowCountsAreRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      AnalyticsSeriesMapper.parse(
        query(),
        ApiSeriesMeta(times = listOf("2026-10-20T00:00:00Z"), interval = 3_600L),
        listOf(AnalyticsSeriesDto(status = "blocked", queries = listOf(-1L)))
      )
    }
    val server = MockWebServer()
    try {
      server.enqueue(MockResponse().setBody("""{"data":[{"status":"blocked","queries":[9223372036854775808]}]}"""))
      server.start()
      val service = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi))
        .build()
        .create(NextDnsApiService::class.java)
      assertThrows(Exception::class.java) {
        kotlinx.coroutines.runBlocking {
          service.getAnalyticsSeries(
            apiKey = "synthetic-key", profileId = "abcdef", endpoint = "status",
            from = "2026-10-20T00:00:00Z", to = "2026-10-20T01:00:00Z", intervalSeconds = 3_600L
          )
        }
      }
    } finally {
      server.shutdown()
    }
  }

  @Test fun retrofitUsesOnlyTheDocumentedSeriesRouteAndParameters() = kotlinx.coroutines.runBlocking {
    val server = MockWebServer()
    try {
      server.enqueue(MockResponse().setBody("""{"data":[],"meta":{"series":{"times":[],"interval":3600}}}"""))
      server.start()
      val service = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi))
        .build()
        .create(NextDnsApiService::class.java)
      service.getAnalyticsSeries(
        apiKey = "synthetic-key",
        profileId = "abcdef",
        endpoint = SeriesMetric.GAFAM.endpoint,
        type = SeriesMetric.GAFAM.type,
        device = "__UNIDENTIFIED__",
        from = "2026-10-20T00:00:00Z",
        to = "2026-10-20T01:00:00Z",
        intervalSeconds = 3_600L
      )

      val request = server.takeRequest()
      assertEquals("/profiles/abcdef/analytics/destinations;series", request.requestUrl!!.encodedPath)
      assertEquals("gafam", request.requestUrl!!.queryParameter("type"))
      assertEquals("__UNIDENTIFIED__", request.requestUrl!!.queryParameter("device"))
      assertEquals("end", request.requestUrl!!.queryParameter("alignment"))
      assertEquals("GMT", request.requestUrl!!.queryParameter("timezone"))
      assertEquals("none", request.requestUrl!!.queryParameter("partials"))
      assertNull(request.requestUrl!!.queryParameter("rootDomains"))
    } finally {
      server.shutdown()
    }
  }

  private fun query() = AnalyticsSeriesQuery(
    profileId = "abcdef",
    deviceId = null,
    fromInclusive = Instant.parse("2026-10-20T00:00:00Z"),
    toExclusive = Instant.parse("2026-10-20T02:00:00Z"),
    metric = SeriesMetric.QUERY_TYPES,
    intervalSeconds = 3_600L
  )
}

