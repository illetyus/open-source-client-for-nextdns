package com.example.audit

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
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
import com.example.data.model.NextDnsProfile
import com.example.data.repository.NextDnsRepository
import com.example.data.security.ApiKeyProtector
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
internal class AuditMainDispatcherRule : TestRule {
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

internal class AuditComposeTestRule(
  private val composeRule: ComposeTestRule = createComposeRule()
) : TestRule, ComposeTestRule by composeRule {
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
  protected val gates = mutableListOf<CountDownLatch>()
  @Volatile protected var response: (RecordedRequest) -> MockResponse = { json("{}", 500) }
  protected fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

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
      diagnosticProbe = { null }, backgroundWorkEnabled = false)
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
