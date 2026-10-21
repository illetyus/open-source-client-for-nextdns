package com.example.audit

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.NextDnsApp
import com.example.R
import com.example.data.api.NextDnsApiService
import com.example.data.api.NextDnsNetworkClient
import com.example.data.api.NextDnsTestService
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.local.NextDnsPreferences
import com.example.data.model.LogsHistoryFilter
import com.example.data.model.LogsSort
import com.example.data.model.LogsTimeWindow
import com.example.data.model.NextDnsProfile
import com.example.data.repository.NextDnsRepository
import com.example.data.security.ApiKeyProtector
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runners.model.Statement
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuditMainDispatcherRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement =
    object : Statement() {
      override fun evaluate() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
          base.evaluate()
        } finally {
          Dispatchers.resetMain()
        }
      }
    }
}

class AuditComposeTestRule(
  private val composeRule: ComposeContentTestRule = createComposeRule()
) : TestRule, ComposeContentTestRule by composeRule {
  override fun apply(base: Statement, description: Description): Statement =
    RuleChain.outerRule(AuditMainDispatcherRule())
      .around(composeRule)
      .apply(base, description)
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AuditFixture {
  protected lateinit var server: MockWebServer
  protected lateinit var repository: NextDnsRepository
  protected lateinit var preferences: NextDnsPreferences
  protected val requests = CopyOnWriteArrayList<String>()
  protected val streamRequests = CopyOnWriteArrayList<String>()
  protected val gates = mutableListOf<CountDownLatch>()
  @Volatile protected var response: (RecordedRequest) -> MockResponse = { json("{}", 500) }
  protected fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
  private val testStreamClient = OkHttpClient.Builder().addInterceptor { chain ->
    val request = chain.request()
    streamRequests += request.url.encodedPath
    Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(200)
      .message("OK")
      .body(ByteArray(0).toResponseBody("text/event-stream".toMediaType()))
      .build()
  }.build()

  @Before fun prepareDisposableFixture() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit()
    preferences = NextDnsPreferences(context, object : ApiKeyProtector {
      override fun encrypt(plainText: String) = "audit:$plainText"
      override fun decrypt(envelope: String) = envelope.removePrefix("audit:")
    })
    preferences.clear()
    preferences.apiKey = "synthetic-audit-key"
    preferences.activeProfileId = "aaaaaa"
    preferences.saveProfiles(listOf(NextDnsProfile("aaaaaa", "Synthetic A", "fp0123456789abcdef")))
    runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        requests += "${request.method} ${request.path}"
        return response(request)
      }
    }
    server.start()
    val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
      .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi)).build()
    repository = NextDnsRepository(preferences,
      apiService = retrofit.create(NextDnsApiService::class.java),
      diagnosticService = retrofit.create(NextDnsTestService::class.java),
      diagnosticProbe = { null }, streamClient = testStreamClient, backgroundWorkEnabled = false)
  }

  @After fun cleanDisposableFixture() {
    gates.forEach { it.countDown() }
    if (::repository.isInitialized) runBlocking { repository.logout() }
    if (::server.isInitialized) server.shutdown()
    if (::preferences.isInitialized) preferences.clear()
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class RepositoryAuditTest : AuditFixture() {
  @get:Rule val mainDispatcherRule = AuditMainDispatcherRule()
  @Test fun emptyAuthoritativeLogRefreshMustRemoveDeletedRemoteHistory() = runBlocking {
    var empty = false
    response = { json(if (empty) """{"data":[]}""" else
      """{"data":[{"timestamp":1735689600000,"domain":"old.synthetic.example","status":"default"}]}""") }
    assertTrue(repository.refreshLogsFromApi())
    assertEquals(1, repository.logs.value.size)
    empty = true
    assertTrue(repository.refreshLogsFromApi())
    assertTrue("Authoritative empty response must not retain remotely deleted log history", repository.logs.value.isEmpty())
  }

  @Test fun invalidHistoryRangeIsRejectedWithoutNetworkRequest() = runBlocking {
    val invalid = LogsHistoryFilter(
      window = LogsTimeWindow(
        fromInclusive = Instant.parse("2026-05-02T00:00:00Z"),
        toExclusive = Instant.parse("2026-05-01T00:00:00Z")
      )
    )

    assertFalse(repository.setLogsHistoryFilter(invalid))
    assertTrue("An invalid range must not issue a request", requests.isEmpty())
  }

  @Test fun historyFilterResetsCursorAndRejectsLatePage() = runBlocking {
    val pageStarted = CountDownLatch(1)
    val releasePage = CountDownLatch(1).also { gates += it }
    val requestedRanges = CopyOnWriteArrayList<List<String?>>()
    response = { request ->
      val url = request.requestUrl!!
      val cursor = url.queryParameter("cursor")
      val from = url.queryParameter("from")
      if (from != null) {
        requestedRanges += listOf(from, url.queryParameter("to"), url.queryParameter("sort"))
      }
      when {
        cursor == "old-cursor" -> {
          pageStarted.countDown()
          check(releasePage.await(10, TimeUnit.SECONDS))
          json("""{"data":[{"timestamp":1735689600000,"domain":"obsolete-page.example","status":"default"}]}""")
        }
        cursor == "new-cursor" -> json("""{"data":[{"timestamp":1735776000000,"domain":"next-page.example","status":"default"}]}""")
        from != null -> json("""{"data":[{"timestamp":1735689600000,"domain":"filtered.example","status":"default"}],"meta":{"pagination":{"cursor":"new-cursor"}}}""")
        else -> json("""{"data":[{"timestamp":1735689600000,"domain":"initial.example","status":"default"}],"meta":{"pagination":{"cursor":"old-cursor"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    val obsoletePage = async(Dispatchers.IO) { repository.loadMoreLogsFromApi() }
    assertTrue(pageStarted.await(10, TimeUnit.SECONDS))

    val filter = LogsHistoryFilter(
      window = LogsTimeWindow(
        fromInclusive = Instant.parse("2026-01-01T00:00:00Z"),
        toExclusive = Instant.parse("2026-02-01T00:00:00Z")
      ),
      sort = LogsSort.OLDEST_FIRST
    )
    assertTrue(repository.setLogsHistoryFilter(filter))
    assertEquals(listOf("filtered.example"), repository.logs.value.map { it.domain })
    assertEquals(listOf("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "asc"), requestedRanges.last())

    releasePage.countDown()
    obsoletePage.await()
    assertEquals(listOf("filtered.example"), repository.logs.value.map { it.domain })
    assertTrue(repository.loadMoreLogsFromApi())
    assertEquals(listOf("filtered.example", "next-page.example"), repository.logs.value.map { it.domain })
    assertTrue(requests.any { it.contains("cursor=old-cursor") })
    assertTrue(requests.any { it.contains("cursor=new-cursor") })
  }

  @Test fun ascendingHistoryDoesNotMixLiveSse() = runBlocking {
    response = { json("""{"data":[]}""") }
    assertTrue(repository.setLogsHistoryFilter(
      LogsHistoryFilter(sort = LogsSort.OLDEST_FIRST)
    ))

    repository.setLiveStreaming(true)
    delay(100)

    assertFalse(repository.isLiveStreaming.value)
    assertFalse("Ascending history cannot open a live SSE stream", streamRequests.any { it.endsWith("/logs/stream") })
  }

  @Test fun previouslyActiveLiveStreamResumesWhenReturningToNewestAvailableHistory() = runBlocking {
    response = { json("""{"data":[]}""") }
    try {
      repository.setLiveStreaming(true)
      withTimeout(2_000) {
        while (streamRequests.none { it.endsWith("/logs/stream") }) delay(10)
      }
      assertTrue(streamRequests.any { it.endsWith("/logs/stream") })

      val bounded = LogsHistoryFilter(
        window = LogsTimeWindow(
          fromInclusive = Instant.parse("2026-01-01T00:00:00Z"),
          toExclusive = Instant.parse("2026-02-01T00:00:00Z")
        )
      )
      assertTrue(repository.setLogsHistoryFilter(bounded))
      assertFalse(repository.isLiveStreaming.value)
      val streamsAfterFilter = streamRequests.count { it.endsWith("/logs/stream") }

      assertTrue(repository.setLogsHistoryFilter(LogsHistoryFilter()))
      assertTrue(repository.isLiveStreaming.value)
      withTimeout(2_000) {
        while (streamRequests.count { it.endsWith("/logs/stream") } <= streamsAfterFilter) delay(10)
      }
      assertTrue(streamRequests.count { it.endsWith("/logs/stream") } > streamsAfterFilter)
    } finally {
      repository.stopLogsStream()
    }
  }

  @Test fun analyticsDevicePaginationIncludesEveryFilterableDevice() = runBlocking {
    response = { request ->
      val url = request.requestUrl!!
      if (url.encodedPath.endsWith("/analytics/devices") &&
        url.queryParameter("limit") == "500" && url.queryParameter("from") == null) {
        when (url.queryParameter("cursor")) {
          null -> json("""{"data":[{"id":"PHONE_A","name":"Annem","queries":10}],"meta":{"pagination":{"cursor":"devices-page-2"}}}""")
          "devices-page-2" -> json("""{"data":[{"id":"__UNIDENTIFIED__","queries":4}],"meta":{"pagination":{"cursor":null}}}""")
          else -> json("""{"data":[]}""")
        }
      } else {
        json("""{"data":[]}""")
      }
    }

    repository.loadActiveProfileDataFromApi()

    assertEquals(setOf("PHONE_A", "__UNIDENTIFIED__"), repository.logDevices.value.map { it.id }.toSet())
    assertTrue(requests.any { it.contains("/analytics/devices") && it.contains("limit=500") })
    assertTrue(requests.any { it.contains("cursor=devices-page-2") })

    response = { request ->
      val url = request.requestUrl!!
      if (url.encodedPath.endsWith("/logs") && url.queryParameter("device") == "__UNIDENTIFIED__") {
        json("""{"data":[{"timestamp":1735689600000,"domain":"unidentified.synthetic.example","status":"blocked"}]}""")
      } else json("""{"data":[]}""")
    }
    assertTrue(repository.setLogsDeviceFilter("__UNIDENTIFIED__"))
    assertEquals("unidentified.synthetic.example", repository.logs.value.single().domain)
    assertTrue(requests.any { it.contains("device=__UNIDENTIFIED__") })
  }

  @Test fun repeatedAnalyticsDeviceCursorKeepsTheLastCompleteInventory() = runBlocking {
    response = { request ->
      val url = request.requestUrl!!
      if (url.encodedPath.endsWith("/analytics/devices") && url.queryParameter("limit") == "500" &&
        url.queryParameter("from") == null) {
        json("""{"data":[{"id":"COMPLETE_DEVICE","name":"Complete","queries":1}],"meta":{"pagination":{"cursor":null}}}""")
      } else {
        json("""{"data":[]}""")
      }
    }
    repository.loadActiveProfileDataFromApi()
    response = { request ->
      val url = request.requestUrl!!
      if (url.encodedPath.endsWith("/analytics/devices") && url.queryParameter("limit") == "500" &&
        url.queryParameter("from") == null) {
        when (url.queryParameter("cursor")) {
          null -> json("""{"data":[{"id":"PHONE_A","name":"Annem","queries":10}],"meta":{"pagination":{"cursor":"repeat"}}}""")
          "repeat" -> json("""{"data":[{"id":"PHONE_B","name":"Other","queries":5}],"meta":{"pagination":{"cursor":"repeat"}}}""")
          else -> json("""{"data":[]}""")
        }
      } else {
        json("""{"data":[]}""")
      }
    }

    repository.loadActiveProfileDataFromApi()

    assertEquals(setOf("COMPLETE_DEVICE"), repository.logDevices.value.map { it.id }.toSet())
  }

  @Test fun analyticsDevicePageLimitKeepsTheLastCompleteInventory() = runBlocking {
    response = { request ->
      if (request.requestUrl!!.encodedPath.endsWith("/analytics/devices")) {
        json("""{"data":[{"id":"COMPLETE_DEVICE","name":"Complete","queries":1}]}""")
      } else json("""{"data":[]}""")
    }
    repository.loadActiveProfileDataFromApi()

    val pageRequests = CopyOnWriteArrayList<String?>()
    response = { request ->
      val url = request.requestUrl!!
      if (url.encodedPath.endsWith("/analytics/devices") && url.queryParameter("limit") == "500" &&
        url.queryParameter("from") == null) {
        pageRequests += url.queryParameter("cursor")
        val page = pageRequests.size
        json("""{"data":[{"id":"PARTIAL_$page","queries":$page}],"meta":{"pagination":{"cursor":"cursor-$page"}}}""")
      } else json("""{"data":[]}""")
    }

    repository.loadActiveProfileDataFromApi()

    assertEquals(10, pageRequests.size)
    assertEquals(null, pageRequests.first())
    assertEquals("cursor-9", pageRequests.last())
    assertEquals(setOf("COMPLETE_DEVICE"), repository.logDevices.value.map { it.id }.toSet())
  }

  @Test fun inFlightLogRefreshCannotRestoreHistoryAfterSuccessfulClear() = runBlocking {
    response = { json("""{"data":[{"timestamp":1735689600000,"domain":"old.example","status":"default"}]}""") }
    assertTrue(repository.refreshLogsFromApi())
    assertEquals("old.example", repository.logs.value.single().domain)

    val entered = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    response = { req ->
      if (req.method == "DELETE") json("{}") else {
        entered.countDown()
        check(release.await(15, TimeUnit.SECONDS))
        json("""{"data":[{"timestamp":1735689600000,"domain":"old.example","status":"default"}]}""")
      }
    }
    val staleRefresh = async(Dispatchers.IO) { repository.refreshLogsFromApi() }
    assertTrue(entered.await(10, TimeUnit.SECONDS))
    assertTrue(repository.clearLogs().isSuccess)
    assertTrue(repository.logs.value.isEmpty())
    release.countDown()
    assertFalse("A response started before clear must be superseded", staleRefresh.await())
    assertTrue(repository.logs.value.isEmpty())
  }

  @Test fun successfulAnalyticsPanelsRemainAvailableWhenOtherPanelsFail() = runBlocking {
    response = { req -> if (req.requestUrl!!.encodedPath.endsWith("/status"))
      json("""{"data":[{"status":"default","queries":100}]}""")
      else json("""{"errors":[{"code":"serverError"}]}""", 500) }
    val success = repository.fetchAnalytics(preferences.apiKey, "aaaaaa")
    assertEquals(14, requests.size)
    assertTrue("One successful panel should allow available analytics to be shown", success)
    assertEquals(100L, repository.analytics.value.totalQueries)
    assertNotNull(repository.analyticsLastSuccessAt.value)
    assertNotNull(repository.analyticsErrorMessage.value)
  }

  @Test fun olderAnalyticsFilterResponseMustNotOverwriteNewSelection() = runBlocking {
    val olderEntered = CountDownLatch(1)
    val releaseOlder = CountDownLatch(1).also { gates += it }
    response = { req ->
      val from = req.requestUrl!!.queryParameter("from")
      if (req.requestUrl!!.encodedPath.endsWith("/status")) {
        if (from == "-24h") {
          olderEntered.countDown()
          check(releaseOlder.await(20, TimeUnit.SECONDS))
        }
        val count = if (from == "-7d") 200 else 100
        json("""{"data":[{"status":"default","queries":$count}]}""")
      } else json("""{"data":[]}""")
    }
    val older = async(Dispatchers.IO) { repository.fetchAnalytics(preferences.apiKey, "aaaaaa", from = "-24h") }
    assertTrue(olderEntered.await(10, TimeUnit.SECONDS))
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa", from = "-7d"))
    assertEquals(200L, repository.analytics.value.totalQueries)
    releaseOlder.countDown()
    older.await()
    assertEquals("Newest selected range must retain its own data", 200L, repository.analytics.value.totalQueries)
  }

  @Test fun choosingGuestMustSupersedeAnInFlightLogin() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(LegalAcceptanceStore(context).acceptCurrentTerms())
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    response = { req ->
      if (req.requestUrl!!.encodedPath == "/profiles") {
        entered.countDown()
        check(release.await(15, TimeUnit.SECONDS))
        json("""{"data":[{"id":"aaaaaa","name":"Synthetic A","fingerprint":"fp0123456789abcdef"}]}""")
      } else json("{}", 500)
    }
    val model = NextDnsViewModel(repository)
    model.loginWithApiKey("synthetic-audit-key")
    val job = NextDnsViewModel::class.java.getDeclaredField("authenticationJob").apply { isAccessible = true }.get(model) as Job
    assertTrue(entered.await(10, TimeUnit.SECONDS))
    model.continueAsGuest()
    assertTrue(model.isGuestMode.value)
    release.countDown()
    withTimeout(15_000) { job.join() }
    assertTrue("Late authentication must not replace the user's newer guest choice", model.isGuestMode.value)
    assertEquals(com.example.data.repository.ApiConnectionStatus.Disconnected, repository.apiStatus.value)
    assertEquals("", repository.apiKey.value)
    assertTrue(repository.profiles.value.isEmpty())
    assertEquals("synthetic-audit-key", preferences.apiKey)
  }
  @Test fun malformedDomainRulesCannotBypassTheForms() = runBlocking {
    for (domain in listOf("a..com", "a-.com", "-a.com", "https://example.com", "a.com/path",
      "*.a..com", "192.168.1.1", "a".repeat(64) + ".com", "a.com\nX-Api-Key: injected")) {
      assertTrue(domain, repository.addToAllowlist(domain).isFailure)
      assertTrue(domain, repository.addToDenylist(domain).isFailure)
    }
    assertTrue("Invalid rules must not send any API request", requests.isEmpty())
  }

  @Test fun unicodeAndWildcardRulesUseCanonicalIdsForWriteAndVerification() = runBlocking {
    var postedId = ""
    response = { req ->
      if (req.method == "POST") {
        postedId = org.json.JSONObject(req.body.readUtf8()).getString("id")
        json("{}")
      } else json("""{"data":[{"id":"$postedId","active":true}]}""")
    }
    assertTrue(repository.addToAllowlist(" BÜCHER.DE. ").isSuccess)
    assertEquals("xn--bcher-kva.de", postedId)
    assertEquals("xn--bcher-kva.de", repository.allowlist.value.single().domain)
    assertTrue(repository.addToDenylist("*.EXAMPLE.COM").isSuccess)
    assertEquals("*.example.com", postedId)
    assertEquals("*.example.com", repository.denylist.value.single().domain)
  }

  @Test fun eachFailedAnalyticsPanelIsReportedAsPartialWhileSuccessfulPanelsAdvance() = runBlocking {
    response = { req -> if (req.requestUrl!!.encodedPath.endsWith("/status"))
      json("""{"data":[{"status":"default","queries":123}]}""") else json("""{"data":[]}""") }
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
    assertEquals(123L, repository.analytics.value.totalQueries)
    for (failedIndex in 0 until 14) {
      var call = 0
      response = { if (call++ == failedIndex) json("{}", 500) else json("""{"data":[]}""") }
      assertTrue("Available panels should still be committed when panel $failedIndex fails",
        repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
      assertNotNull(repository.analyticsLastSuccessAt.value)
      assertNotNull(repository.analyticsErrorMessage.value)
    }

    val previous = repository.analytics.value
    val successTime = repository.analyticsLastSuccessAt.value
    response = { json("{}", 500) }
    assertFalse("A fully failed refresh must not count as success", repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
    assertEquals(previous, repository.analytics.value)
    assertEquals(successTime, repository.analyticsLastSuccessAt.value)
    assertNotNull(repository.analyticsErrorMessage.value)

    response = { json("""{"data":[]}""") }
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
    assertEquals(0L, repository.analytics.value.totalQueries)
    assertNull(repository.analyticsErrorMessage.value)
  }

  @Test fun failedCountryPanelRetainsItsLastSuccessfulSnapshot() = runBlocking {
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/status") ->
          json("""{"data":[{"status":"default","queries":100}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/destinations") ->
          json("""{"data":[{"code":"US","queries":42,"domains":["dns.example"]}]}""")
        else -> json("""{"data":[]}""")
      }
    }
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
    val previousCountries = repository.analytics.value.topCountries
    assertEquals(listOf("US"), previousCountries.map { it.code })

    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/status") ->
          json("""{"data":[{"status":"default","queries":200}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/destinations") -> json("{}", 500)
        request.requestUrl?.encodedPath.orEmpty().contains("/analytics/") -> json("""{"data":[]}""")
        else -> json("{}", 500)
      }
    }
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
    assertEquals(200L, repository.analytics.value.totalQueries)
    assertEquals("A failed country request should retain the last usable map data",
      previousCountries, repository.analytics.value.topCountries)
    assertNotNull(repository.analyticsErrorMessage.value)
  }

  @Test fun malformedOrSemanticAnalyticsFailureDoesNotLookSuccessful() = runBlocking {
    for (body in listOf("{}", """{"errors":[{"code":"invalid"}]}""", "not-json")) {
      response = { json(body) }
      assertFalse(repository.fetchAnalytics(preferences.apiKey, "aaaaaa"))
      assertNull(repository.analyticsLastSuccessAt.value)
      assertNotNull(repository.analyticsErrorMessage.value)
    }
  }

  @Test fun obsoleteAnalyticsFailureCannotReplaceNewSelectionSuccess() = runBlocking {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    response = { req ->
      if (req.requestUrl!!.queryParameter("from") == "-24h") {
        entered.countDown(); check(release.await(20, TimeUnit.SECONDS)); json("{}", 500)
      } else json("""{"data":[]}""")
    }
    val old = async(Dispatchers.IO) { repository.fetchAnalytics(preferences.apiKey, "aaaaaa", from = "-24h") }
    assertTrue(entered.await(10, TimeUnit.SECONDS))
    assertTrue(repository.fetchAnalytics(preferences.apiKey, "aaaaaa", from = "-7d"))
    release.countDown()
    old.await()
    assertNull(repository.analyticsErrorMessage.value)
    assertNotNull(repository.analyticsLastSuccessAt.value)
  }

  @Test fun invalidLogSnapshotPreservesHistoryAndNewerRefreshWins() = runBlocking {
    response = { json("""{"data":[{"timestamp":1735689600000,"domain":"keep.example","status":"default"}]}""") }
    assertTrue(repository.refreshLogsFromApi())
    for (body in listOf("{}", """{"errors":[{"code":"invalid"}]}""")) {
      response = { json(body) }
      assertFalse(repository.refreshLogsFromApi())
      assertEquals("keep.example", repository.logs.value.single().domain)
    }
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    var call = 0
    response = {
      if (call++ == 0) {
        entered.countDown(); check(release.await(20, TimeUnit.SECONDS))
        json("""{"data":[{"timestamp":1735689600000,"domain":"obsolete.example","status":"default"}]}""")
      } else json("""{"data":[]}""")
    }
    val old = async(Dispatchers.IO) { repository.refreshLogsFromApi() }
    assertTrue(entered.await(10, TimeUnit.SECONDS))
    assertTrue(repository.refreshLogsFromApi())
    release.countDown(); old.await()
    assertTrue(repository.logs.value.isEmpty())
  }

  @Test fun cancellingOldAnalyticsCannotClearNewRequestLoading() = runBlocking {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1).also { gates += it }
    val secondEntered = CountDownLatch(1)
    val secondRelease = CountDownLatch(1).also { gates += it }
    response = { req ->
      if (req.requestUrl!!.encodedPath.endsWith("/status")) {
        if (req.requestUrl!!.queryParameter("from") == "-24h") {
          entered.countDown(); check(release.await(20, TimeUnit.SECONDS))
        } else {
          secondEntered.countDown(); check(secondRelease.await(20, TimeUnit.SECONDS))
        }
      }
      json("""{"data":[]}""")
    }
    val model = NextDnsViewModel(repository)
    val existingJobs = model.viewModelScope.coroutineContext.job.children.toSet()
    model.refreshAnalytics(null, "-24h")
    assertTrue(entered.await(10, TimeUnit.SECONDS))
    val oldJob = model.viewModelScope.coroutineContext.job.children.single { it !in existingJobs }
    model.refreshAnalytics(null, "-7d")
    assertTrue(secondEntered.await(10, TimeUnit.SECONDS))
    release.countDown()
    withTimeout(10_000) { oldJob.join() }
    assertTrue("Only the newest request owns the loading indicator", model.isAnalyticsLoading.value)
    secondRelease.countDown()
    withTimeout(5_000) { while (model.isAnalyticsLoading.value) delay(10) }
    assertFalse(model.isAnalyticsLoading.value)
  }

}

