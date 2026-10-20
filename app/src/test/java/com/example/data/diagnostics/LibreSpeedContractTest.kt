package com.example.data.diagnostics

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class LibreSpeedContractTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun serverConfigurationRequiresAuthorizedHttpsAndSafeBackendPath() {
    val http = validateLibreSpeedServer(
      baseUrl = "http://speed.example/",
      privacyPolicyUrl = "https://speed.example/privacy",
      backendPath = "backend",
      authorizationConfirmed = true
    )
    assertEquals(SpeedTestConfigError.HTTPS_REQUIRED, http.error)

    val credentials = validateLibreSpeedServer(
      baseUrl = "https://user:password@speed.example/",
      privacyPolicyUrl = "https://speed.example/privacy",
      backendPath = "backend",
      authorizationConfirmed = true
    )
    assertEquals(SpeedTestConfigError.CREDENTIALS_NOT_ALLOWED, credentials.error)

    val traversal = validateLibreSpeedServer(
      baseUrl = "https://speed.example/",
      privacyPolicyUrl = "https://speed.example/privacy",
      backendPath = "../private",
      authorizationConfirmed = true
    )
    assertEquals(SpeedTestConfigError.INVALID_BACKEND_PATH, traversal.error)

    val unapproved = validateLibreSpeedServer(
      baseUrl = "https://speed.example/",
      privacyPolicyUrl = "https://speed.example/privacy",
      backendPath = "backend",
      authorizationConfirmed = false
    )
    assertEquals(SpeedTestConfigError.AUTHORIZATION_REQUIRED, unapproved.error)
    assertNotNull("Valid settings can be saved before per-test authorization", unapproved.server)

    val privatePolicyQuery = validateLibreSpeedServer(
      baseUrl = "https://speed.example/",
      privacyPolicyUrl = "https://speed.example/privacy?token=secret",
      backendPath = "backend",
      authorizationConfirmed = true
    )
    assertEquals(SpeedTestConfigError.INVALID_PRIVACY_URL, privatePolicyQuery.error)
  }

  @Test
  fun serverConfigurationBuildsEndpointsUnderConfiguredBasePath() {
    val validation = validateLibreSpeedServer(
      baseUrl = "https://speed.example/speed/",
      privacyPolicyUrl = "https://speed.example/privacy",
      backendPath = "backend",
      authorizationConfirmed = true
    )

    val config = assertNotNullAndReturn(validation.server)
    assertEquals("https://speed.example/speed/backend/empty.php", config.pingUrl.toString())
    assertEquals("https://speed.example/speed/backend/garbage.php", config.downloadUrl.toString())
    assertEquals("https://speed.example/speed/backend/empty.php", config.uploadUrl.toString())
  }

  @Test
  fun pingUsesOnlyConfiguredHostAndMeasuresThreeHttpRoundTrips() = runBlocking {
    repeat(3) { server.enqueue(MockResponse().setResponseCode(200)) }
    val config = validTestServer()
    val client = LibreSpeedClient(
      httpClient = OkHttpClient(),
      allowHttpForTests = true
    )

    val result = client.measurePing(config, samples = 3)

    assertEquals(3, server.requestCount)
    assertTrue(result.latencyMs >= 0.0)
    assertTrue(result.jitterMs >= 0.0)
    val request = server.takeRequest()
    assertEquals("/speed/backend/empty.php", request.path)
    assertEquals(null, request.getHeader("Authorization"))
    assertEquals(null, request.getHeader("Cookie"))
  }

  @Test
  fun downloadStreamsBoundedSyntheticBytesAndReportsMbps() = runBlocking {
    val bytes = ByteArray((LibreSpeedClient.MIN_DOWNLOAD_MEBIBYTES * LibreSpeedClient.BYTES_PER_MEBIBYTE).toInt()) { (it % 251).toByte() }
    server.enqueue(MockResponse().addHeader("Content-Type", "application/octet-stream").setBody(Buffer().write(bytes)))
    val config = validTestServer()
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val result = client.measureDownload(config, maxBytes = bytes.size.toLong())

    assertEquals(bytes.size.toLong(), result.bytesTransferred)
    assertTrue(result.megabitsPerSecond > 0.0)
    val request = server.takeRequest()
    assertTrue(request.path.orEmpty().startsWith("/speed/backend/garbage.php?ckSize=4"))
    assertEquals("identity", request.getHeader("Accept-Encoding"))
  }

  @Test
  fun downloadRejectsSuccessfulHtmlFallbackInsteadOfReportingASpeed() = runBlocking {
    server.enqueue(MockResponse().addHeader("Content-Type", "text/html; charset=utf-8").setBody("<html>Not Found</html>"))
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val error = runCatching {
      client.measureDownload(validTestServer(), maxBytes = LibreSpeedClient.MIN_DOWNLOAD_MEBIBYTES * LibreSpeedClient.BYTES_PER_MEBIBYTE)
    }.exceptionOrNull()

    assertNotNull("A 200 HTML error page must not become a successful speed result", error)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun downloadRejectsTruncatedBinaryPayload() = runBlocking {
    val expectedBytes = LibreSpeedClient.MIN_DOWNLOAD_MEBIBYTES * LibreSpeedClient.BYTES_PER_MEBIBYTE
    server.enqueue(
      MockResponse()
        .addHeader("Content-Type", "application/octet-stream")
        .setBody(Buffer().write(ByteArray(8_192)))
        .setHeader("Content-Length", expectedBytes.toString())
    )
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val error = runCatching { client.measureDownload(validTestServer(), maxBytes = expectedBytes) }.exceptionOrNull()

    assertNotNull("A prematurely terminated payload must not produce a measurement", error)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun uploadSendsOnlyBoundedSyntheticBodyToConfiguredHttpsCompatibleEndpoint() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(200))
    val config = validTestServer()
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val result = client.measureUpload(config, bytesToSend = 4_096)

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/speed/backend/empty.php", request.path)
    assertEquals(4_096L, request.body.size)
    assertEquals(4_096L, result.bytesTransferred)
    assertTrue(result.megabitsPerSecond > 0.0)
  }

  @Test
  fun uploadRejectsPayloadsAboveTheOneMegabyteProductLimit() = runBlocking {
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val error = runCatching {
      client.measureUpload(validTestServer(), bytesToSend = LibreSpeedClient.MAX_UPLOAD_BYTES + 1)
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals(0, server.requestCount)
  }

  @Test
  fun unexpectedRedirectIsRejectedWithoutFollowingAnotherHost() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://other.example/"))
    val config = validTestServer()
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val error = runCatching { client.measurePing(config, samples = 1) }.exceptionOrNull()

    assertNotNull(error)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun oversizedDownloadIsStoppedAtConfiguredByteLimit() = runBlocking {
    val maxBytes = LibreSpeedClient.MIN_DOWNLOAD_MEBIBYTES * LibreSpeedClient.BYTES_PER_MEBIBYTE
    val bytes = ByteArray((maxBytes + 1).toInt()) { 1 }
    server.enqueue(MockResponse().addHeader("Content-Type", "application/octet-stream").setBody(Buffer().write(bytes)))
    val client = LibreSpeedClient(OkHttpClient(), allowHttpForTests = true)

    val error = runCatching { client.measureDownload(validTestServer(), maxBytes = maxBytes) }.exceptionOrNull()

    assertNotNull(error)
    assertEquals(1, server.requestCount)
  }

  private fun validTestServer(): ValidatedLibreSpeedServer {
    val baseUrl = server.url("/speed/").toString()
    return assertNotNullAndReturn(
      validateLibreSpeedServer(
        baseUrl = baseUrl,
        privacyPolicyUrl = "https://privacy.example/policy",
        backendPath = "backend",
        authorizationConfirmed = true,
        allowHttpForTests = true
      ).server
    )
  }

  private fun <T : Any> assertNotNullAndReturn(value: T?): T {
    assertNotNull(value)
    return value!!
  }
}
