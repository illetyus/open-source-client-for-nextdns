package com.example.macrobenchmark

import android.content.Intent
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Local HTTPS API hosted by the instrumentation process, outside measured app startup. */
internal class SyntheticNextDnsApi private constructor(
  private val server: MockWebServer,
  val baseUrl: String,
  val certificateBase64: String
) : AutoCloseable {
  fun intentForFixture(): Intent = Intent()
    .setAction(Intent.ACTION_MAIN)
    .setClassName(TARGET_PACKAGE, FIXTURE_ACTIVITY)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .putExtra(EXTRA_BASE_URL, baseUrl)
    .putExtra(EXTRA_CERTIFICATE_BASE64, certificateBase64)

  fun shellLaunchCommand(): String =
    "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
      "-n $TARGET_PACKAGE/$FIXTURE_ACTIVITY " +
      "--es $EXTRA_BASE_URL $baseUrl --es $EXTRA_CERTIFICATE_BASE64 $certificateBase64"

  override fun close() {
    server.shutdown()
  }

  companion object {
    const val TARGET_PACKAGE = "com.aistudio.nextdns.mgrqvt.benchmark"
    const val FIXTURE_ACTIVITY = "com.example.BenchmarkFixtureActivity"
    const val EXTRA_BASE_URL = "benchmark_base_url"
    const val EXTRA_CERTIFICATE_BASE64 = "benchmark_certificate_base64"
    private const val PROFILE_ID = "b00b1e"
    private const val PAGE_TWO_CURSOR = "benchmark-page-2"

    fun start(): SyntheticNextDnsApi {
      val fixtureNow = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
      val certificate = HeldCertificate.Builder()
        .commonName("localhost")
        .addSubjectAlternativeName("localhost")
        .build()
      val serverCertificates = HandshakeCertificates.Builder()
        .heldCertificate(certificate)
        .build()
      val server = MockWebServer().apply {
        useHttps(serverCertificates.sslSocketFactory(), false)
        dispatcher = SyntheticApiDispatcher(fixtureNow)
        start()
      }
      return SyntheticNextDnsApi(
        server = server,
        baseUrl = server.url("/").toString(),
        certificateBase64 = Base64.getEncoder().encodeToString(certificate.certificate.encoded)
      )
    }
  }

  private class SyntheticApiDispatcher(private val fixtureNow: Instant) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
      val path = request.requestUrl?.encodedPath.orEmpty()
      val responseBody = when {
        path == "/profiles" -> """{"data":[{"id":"$PROFILE_ID","name":"Benchmark profile","fingerprint":"bench01"}]}"""
        path.matches(Regex("/profiles/[^/]+")) -> """{"data":{"id":"$PROFILE_ID","name":"Benchmark profile","fingerprint":"bench01"}}"""
        path.endsWith("/logs") -> syntheticLogs(request)
        path.contains("/analytics/") && path.endsWith(";series") -> syntheticSeries(request)
        path.contains("/analytics/") -> syntheticAnalytics(request)
        path.endsWith("/security") || path.endsWith("/privacy") ||
          path.endsWith("/parentalControl") || path.endsWith("/settings") -> """{"data":{}}"""
        path.endsWith("/allowlist") || path.endsWith("/denylist") ||
          path.contains("/privacy/") || path.contains("/parentalControl/") -> """{"data":[]}"""
        path.startsWith("/profiles/") -> """{"data":{}}"""
        else -> return MockResponse().setResponseCode(404).setBody("synthetic benchmark route not found")
      }
      return MockResponse()
        .setHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(responseBody)
    }

    private fun syntheticLogs(request: RecordedRequest): String {
      val secondPage = request.requestUrl?.queryParameter("cursor") == PAGE_TWO_CURSOR
      val firstIndex = if (secondPage) 100 else 0
      val logs = (firstIndex until firstIndex + 100).joinToString(",") { index ->
        val timestamp = fixtureNow.minusSeconds(index.toLong() * 60L).toString()
        val status = if (index % 5 == 0) "blocked" else "allowed"
        """{"id":"bench-log-$index","timestamp":"$timestamp","domain":"site-$index.invalid","root":"site-$index.invalid","client":"Benchmark device","device":{"id":"bench-device","name":"Benchmark device","model":"synthetic"},"status":"$status","protocol":"DOH","reasons":[{"id":"blocklist","name":"Blocklist"}]}"""
      }
      val pagination = if (secondPage) "" else ",\"meta\":{\"pagination\":{\"cursor\":\"$PAGE_TWO_CURSOR\"}}"
      return """{"data":[$logs]$pagination}"""
    }

    private fun syntheticAnalytics(request: RecordedRequest): String {
      val type = request.requestUrl?.queryParameter("type")
      val path = request.requestUrl?.encodedPath.orEmpty()
      val data = when {
        path.endsWith("/status") -> """[{"status":"blocked","queries":32},{"status":"allowed","queries":168}]"""
        path.endsWith("/devices") -> """[{"id":"bench-device","name":"Benchmark device","model":"synthetic","queries":200}]"""
        path.endsWith("/domains") -> """[{"domain":"site-1.invalid","root":"site-1.invalid","queries":80}]"""
        path.endsWith("/reasons") -> """[{"id":"blocklist","name":"Blocklist","queries":32}]"""
        path.endsWith("/destinations") && type == "gafam" -> """[{"company":"Google","queries":58},{"company":"Microsoft","queries":23}]"""
        path.endsWith("/destinations") -> """[{"code":"US","name":"United States","queries":142,"domains":["site-1.invalid"]},{"code":"DE","name":"Germany","queries":58}]"""
        path.endsWith("/dnssec") -> """[{"validated":true,"queries":190},{"validated":false,"queries":10}]"""
        path.endsWith("/encryption") -> """[{"encrypted":true,"queries":200}]"""
        path.endsWith("/protocols") -> """[{"protocol":"DOH","queries":200}]"""
        path.endsWith("/queryTypes") -> """[{"type":1,"name":"A","queries":145},{"type":28,"name":"AAAA","queries":55}]"""
        path.endsWith("/ipVersions") -> """[{"version":4,"queries":120},{"version":6,"queries":80}]"""
        path.endsWith("/ips") -> """[{"ip":"192.0.2.1","network":{"cellular":false,"vpn":false,"isp":"Synthetic ISP","asn":64500},"geo":{"countryCode":"US","country":"United States","city":"Test City"},"queries":200}]"""
        else -> "[]"
      }
      return """{"data":$data}"""
    }

    private fun syntheticSeries(request: RecordedRequest): String {
      val url = request.requestUrl
      val interval = url?.queryParameter("interval")?.toLongOrNull() ?: 3_600L
      val from = url?.queryParameter("from")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: fixtureNow.minus(Duration.ofHours(24))
      val to = url?.queryParameter("to")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: fixtureNow
      val count = (Duration.between(from, to).seconds.coerceAtLeast(1) + interval - 1)
        .div(interval).toInt().coerceIn(1, 100)
      val times = (0 until count).joinToString(",") { index -> "\"${from.plusSeconds(interval * index)}\"" }
      val counts = (0 until count).joinToString(",") { index -> (20 + index % 7).toString() }
      val path = url?.encodedPath.orEmpty()
      val type = url?.queryParameter("type")
      val row = when {
        path.endsWith("/status;series") -> """{"status":"allowed","queries":[$counts]}"""
        path.endsWith("/devices;series") -> """{"id":"bench-device","name":"Benchmark device","queries":[$counts]}"""
        path.endsWith("/domains;series") -> """{"domain":"site-1.invalid","root":"site-1.invalid","queries":[$counts]}"""
        path.endsWith("/reasons;series") -> """{"id":"blocklist","name":"blocklist","queries":[$counts]}"""
        path.endsWith("/destinations;series") && type == "gafam" -> """{"company":"Google","queries":[$counts]}"""
        path.endsWith("/destinations;series") -> """{"code":"US","name":"United States","queries":[$counts]}"""
        path.endsWith("/dnssec;series") -> """{"validated":true,"queries":[$counts]}"""
        path.endsWith("/encryption;series") -> """{"encrypted":true,"queries":[$counts]}"""
        path.endsWith("/protocols;series") -> """{"protocol":"DOH","queries":[$counts]}"""
        path.endsWith("/ips;series") -> """{"ip":"192.0.2.1","queries":[$counts]}"""
        path.endsWith("/queryTypes;series") -> """{"type":1,"name":"A","queries":[$counts]}"""
        path.endsWith("/ipVersions;series") -> """{"version":4,"queries":[$counts]}"""
        else -> """{"status":"allowed","queries":[$counts]}"""
      }
      return """{"meta":{"series":{"times":[$times],"interval":$interval}},"data":[$row]}"""
    }
  }
}
