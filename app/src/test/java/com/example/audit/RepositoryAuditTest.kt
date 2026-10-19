package com.example.audit

import android.content.Context
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

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
    Dispatchers.setMain(UnconfinedTestDispatcher())
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
    Dispatchers.resetMain()
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class RepositoryAuditTest : AuditFixture() {
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

  @Test fun failedAnalyticsPanelsMustNotBeReportedAsFreshCompleteSuccess() = runBlocking {
    response = { req -> if (req.requestUrl!!.encodedPath.endsWith("/status"))
      json("""{"data":[{"status":"default","queries":100}]}""")
      else json("""{"errors":[{"code":"serverError"}]}""", 500) }
    val success = repository.fetchAnalytics(preferences.apiKey, "aaaaaa")
    assertEquals(14, requests.size)
    assertFalse("One successful status panel cannot conceal failures in the other 13 panels", success)
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
  }
}
