package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class NextDnsApiContractTest {

  private lateinit var server: MockWebServer
  private lateinit var api: NextDnsApiService

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()

    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()

    api = Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(NextDnsApiService::class.java)
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun credentialedClient_doesNotFollowRedirects() {
    assertFalse(NextDnsNetworkClient.client.followRedirects)
    assertFalse(NextDnsNetworkClient.client.followSslRedirects)
  }

  @Test
  fun profileCreate_acceptsIdOnlyResponse() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"data":{"id":"abc123"}}""")
    )

    val response = api.createProfile("test-key", NameRequest("Test Profile"))

    assertTrue(response.isSuccessful)
    assertEquals("abc123", response.body()?.data?.id)
    assertFalse(response.body().hasApiErrors())
  }

  @Test
  fun http200WithErrors_isNotSemanticSuccess() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"errors":[{"code":"invalid","detail":"Invalid profile name","source":{"parameter":"name"}}]}"""
        )
    )

    val response = api.createProfile("test-key", NameRequest("Bad Profile"))

    assertTrue(response.isSuccessful)
    assertTrue(response.body().hasApiErrors())
    assertFalse(response.body().isSemanticallySuccessful())
    assertEquals("name", response.body()?.errors?.firstOrNull()?.source?.parameter)
  }

  @Test
  fun countryDestinationResponseRetainsItsDomainSamples() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"data":[{"code":"US","domains":["dns.example","mail.example"],"queries":42}]}""")
    )

    val response = api.getAnalyticsDestinations("test-key", "abc123", type = "countries")
    val country = response.body()?.data?.single()

    assertEquals("US", country?.code)
    assertEquals(42L, country?.queries)
    assertEquals(listOf("dns.example", "mail.example"), country?.domains)
  }

  @Test
  fun logsResponse_parsesPaginationAndStreamMetadata() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":[],"meta":{"pagination":{"cursor":"cursor-2"},"stream":{"id":"stream-42"}}}"""
        )
    )

    val response = api.getLogs(
      apiKey = "test-key",
      profileId = "abcdef",
      limit = 100,
      from = "-1h",
      to = "now",
      sort = "asc",
      device = "device-123",
      cursor = "cursor-1",
      raw = 1
    )

    assertTrue(response.isSuccessful)
    assertEquals("cursor-2", response.body()?.meta?.pagination?.cursor)
    assertEquals("stream-42", response.body()?.meta?.stream?.id)

    val request = server.takeRequest()
    val path = request.path.orEmpty()
    assertTrue(path.contains("from=-1h"))
    assertTrue(path.contains("to=now"))
    assertTrue(path.contains("sort=asc"))
    assertTrue(path.contains("device=device-123"))
    assertTrue(path.contains("cursor=cursor-1"))
    assertFalse(path.contains("before="))
  }

  @Test
  fun configurationGets_parseNextDnsDataEnvelope() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":{"threatIntelligenceFeeds":false,"googleSafeBrowsing":true,"tlds":[{"id":"ru"}]}}"""
        )
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":{"disguisedTrackers":true,"allowAffiliate":false,"blocklists":[{"id":"oisd"}],"natives":[{"id":"apple"}]}}"""
        )
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":{"safeSearch":true,"youtubeRestrictedMode":false,"blockBypass":false,"services":[{"id":"tiktok","active":true}],"categories":[{"id":"porn","active":true}]}}"""
        )
    )

    val security = api.getSecurity("test-key", "profile-1")
    val privacy = api.getPrivacy("test-key", "profile-1")
    val parental = api.getParentalControl("test-key", "profile-1")

    assertTrue(security.body().isSemanticallySuccessful())
    assertEquals(false, security.body()?.data?.threatIntelligenceFeeds)
    assertEquals("ru", security.body()?.data?.tlds?.firstOrNull()?.id)

    assertTrue(privacy.body().isSemanticallySuccessful())
    assertEquals("oisd", privacy.body()?.data?.blocklists?.firstOrNull()?.id)
    assertEquals(true, privacy.body()?.data?.disguisedTrackers)

    assertTrue(parental.body().isSemanticallySuccessful())
    assertEquals("tiktok", parental.body()?.data?.services?.firstOrNull()?.id)
    assertEquals(false, parental.body()?.data?.blockBypass)
  }

  @Test
  fun profilesRequest_supportsCursorPagination() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":[{"id":"p2","name":"Second"}],"meta":{"pagination":{"cursor":null}}}"""
        )
    )

    val response = api.getProfiles("test-key", cursor = "cursor-1")

    assertTrue(response.isSuccessful)
    assertEquals("p2", response.body()?.data?.firstOrNull()?.id)

    val request = server.takeRequest()
    assertTrue(request.path.orEmpty().contains("cursor=cursor-1"))
  }

  @Test
  fun analyticsQueryTypes_supportsDocumentedWindowAndPaginationParams() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"data":[{"type":28,"name":"AAAA","queries":356230}],"meta":{"pagination":{"cursor":"next-page"}}}"""
        )
    )

    val response = api.getAnalyticsQueryTypes(
      apiKey = "test-key",
      profileId = "profile-1",
      from = "-7d",
      to = "now",
      limit = 50,
      cursor = "cursor-1"
    )

    assertTrue(response.isSuccessful)
    assertEquals("AAAA", response.body()?.data?.firstOrNull()?.name)
    assertEquals(28, response.body()?.data?.firstOrNull()?.type)
    assertEquals("next-page", response.body()?.meta?.pagination?.cursor)

    val path = server.takeRequest().path.orEmpty()
    assertTrue(path.contains("from=-7d"))
    assertTrue(path.contains("to=now"))
    assertTrue(path.contains("limit=50"))
    assertTrue(path.contains("cursor=cursor-1"))
  }

  @Test
  fun securityMutation_sendsOnlyChangedField() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"data":null}""")
    )

    val response = api.updateSecurity(
      apiKey = "test-key",
      profileId = "profile-1",
      body = SecurityUpdateRequest(googleSafeBrowsing = false)
    )

    assertTrue(response.isSuccessful)
    assertTrue(response.body()?.errors.isNullOrEmpty())

    val request = server.takeRequest()
    val body = request.body.readUtf8()
    assertTrue(body.contains("\"googleSafeBrowsing\":false"))
    assertFalse(body.contains("threatIntelligenceFeeds"))
    assertFalse(body.contains("aiThreatDetection"))
    assertFalse(body.contains("cryptojacking"))
  }

  @Test
  fun mutationHttp200WithErrors_isRejectedByContract() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """{"errors":[{"code":"invalid","detail":"Setting cannot be changed","source":{"parameter":"safeSearch"}}]}"""
        )
    )

    val response = api.updateParentalControl(
      apiKey = "test-key",
      profileId = "profile-1",
      body = ParentalControlUpdateRequest(safeSearch = true)
    )

    assertTrue(response.isSuccessful)
    assertEquals("Setting cannot be changed", response.body()?.errors?.firstOrNull()?.detail)
    assertEquals("safeSearch", response.body()?.errors?.firstOrNull()?.source?.parameter)
  }

  @Test
  fun settingsLogsMutation_doesNotOverwriteSiblingDropField() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"data":null}""")
    )

    api.updateSettingsLogs(
      apiKey = "test-key",
      profileId = "profile-1",
      body = SettingsLogsUpdateRequest(
        drop = SettingsLogsDropDto(ip = true)
      )
    )

    val body = server.takeRequest().body.readUtf8()
    assertTrue(body.contains("\"ip\":true"))
    assertFalse(body.contains("\"domain\""))
    assertFalse(body.contains("\"retention\""))
    assertFalse(body.contains("\"location\""))
  }

  @Test
  fun logDownload_requestsPublicUrlWithoutFollowingCredentialedRedirect() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"data":{"url":"https://download.example.test/logs.csv"}}""")
    )

    val response = api.getLogsDownloadLink(
      apiKey = "test-key",
      profileId = "profile-1",
      redirect = 0
    )

    assertTrue(response.isSuccessful)
    assertTrue(
      response.body()?.string()?.contains("https://download.example.test/logs.csv") == true
    )

    val request = server.takeRequest()
    assertTrue(request.path.orEmpty().contains("/profiles/profile-1/logs/download"))
    assertTrue(request.path.orEmpty().contains("redirect=0"))
    assertEquals("test-key", request.getHeader("X-Api-Key"))
  }

  @Test
  fun retentionCodec_usesSecondsAtApiBoundary() {
    assertEquals(21_600, LogRetentionCodec.toSeconds("6 saat"))
    assertEquals(86_400, LogRetentionCodec.toSeconds("1 gün"))
    assertEquals(604_800, LogRetentionCodec.toSeconds("1 hafta"))
    assertEquals(2_592_000, LogRetentionCodec.toSeconds("1 ay"))
    assertEquals(7_776_000, LogRetentionCodec.toSeconds("3 ay"))
    assertEquals(15_552_000, LogRetentionCodec.toSeconds("6 ay"))
    assertEquals(31_536_000, LogRetentionCodec.toSeconds("1 yıl"))
    assertEquals(63_072_000, LogRetentionCodec.toSeconds("2 yıl"))

    assertEquals("3 ay", LogRetentionCodec.toLabel(7_776_000))
    assertNull(LogRetentionCodec.toLabel(123))
    assertNull(LogRetentionCodec.toSeconds("Bilinmiyor"))
  }
}
