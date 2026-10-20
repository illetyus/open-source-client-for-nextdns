package com.example.data.repository

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.NextDnsApp
import com.example.data.api.NextDnsApiService
import com.example.data.api.NextDnsNetworkClient
import com.example.data.api.NextDnsTestResponse
import com.example.data.api.NextDnsTestService
import com.example.data.local.NextDnsPreferences
import com.example.data.model.NextDnsProfile
import com.example.data.security.ApiKeyProtector
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class RepositoryRegressionTest {
  private class FixtureProtector : ApiKeyProtector {
    override fun encrypt(plainText: String) = "fixture:$plainText"
    override fun decrypt(envelope: String) = envelope.removePrefix("fixture:")
  }

  private lateinit var server: MockWebServer
  private lateinit var preferences: NextDnsPreferences
  private lateinit var repository: NextDnsRepository
  private lateinit var api: NextDnsApiService
  private lateinit var testApi: NextDnsTestService
  private val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
  private val gates = mutableListOf<CountDownLatch>()
  @Volatile private var response: (RecordedRequest) -> MockResponse = { json("{}", 404) }

  @Before fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit()
    preferences = NextDnsPreferences(context, FixtureProtector())
    preferences.clear()
    preferences.apiKey = "fixture-account-a"
    preferences.activeProfileId = "aaaaaa"
    preferences.saveProfiles(listOf(NextDnsProfile("aaaaaa", "A"), NextDnsProfile("bbbbbb", "B")))
    runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl
        requests.add("${request.method} ${url?.encodedPath.orEmpty()}${url?.encodedQuery?.let { "?$it" }.orEmpty()}")
        return response(request)
      }
    }
    server.start()
    val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
      .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi)).build()
    api = retrofit.create(NextDnsApiService::class.java)
    testApi = retrofit.create(NextDnsTestService::class.java)
    repository = newRepository()
  }

  private fun newRepository(probe: suspend (String?) -> NextDnsTestResponse? = { null }) =
    NextDnsRepository(preferences, apiService = api, diagnosticService = testApi,
      diagnosticProbe = probe, backgroundWorkEnabled = false)

  @After fun tearDown() {
    gates.forEach { it.countDown() }
    server.shutdown()
    preferences.clear()
  }

  private fun gate() = CountDownLatch(1).also { gates.add(it) }
  private fun await(latch: CountDownLatch) {
    assertTrue("Fixture request must reach its barrier", latch.await(5, TimeUnit.SECONDS))
  }
  private suspend fun assertCancelled(request: Deferred<*>) {
    try {
      request.await()
      fail("An obsolete request must not report success")
    } catch (_: CancellationException) { }
  }

  @Test fun lateLogsCannotPopulateAnotherProfile() = runBlocking {
    val entered = gate(); val release = gate()
    response = { entered.countDown(); await(release); json("""{"data":[{"domain":"old.example","timestamp":"2026-10-10T00:00:00Z","status":"default"}]}""") }
    val pending = async(Dispatchers.IO) { repository.refreshLogsFromApi() }
    await(entered)
    repository.setActiveProfile("bbbbbb")
    release.countDown()
    assertCancelled(pending)
    assertTrue(repository.logs.value.isEmpty())
    assertEquals("bbbbbb", preferences.activeProfileId)
  }

  @Test fun profileRoundTripStillRejectsOldLogs() = runBlocking {
    val entered = gate(); val release = gate()
    response = { entered.countDown(); await(release); json("""{"data":[{"domain":"old.example"}]}""") }
    val pending = async(Dispatchers.IO) { repository.refreshLogsFromApi() }
    await(entered)
    repository.setActiveProfile("bbbbbb")
    repository.setActiveProfile("aaaaaa")
    release.countDown()
    assertCancelled(pending)
    assertTrue(repository.logs.value.isEmpty())
  }

  @Test fun olderLogPagesUseTheNextCursorAndAppendWithoutDuplicates() = runBlocking {
    response = { request ->
      if (request.requestUrl?.queryParameter("cursor") == "cursor-older") {
        json("""{"data":[{"domain":"older.example","timestamp":"2026-10-19T23:00:00Z","device":{"id":"device-a","name":"Annem"},"status":"default"}],"meta":{"pagination":{"cursor":null}}}""")
      } else {
        json("""{"data":[{"domain":"newer.example","timestamp":"2026-10-20T00:00:00Z","device":{"id":"device-a","name":"Annem"},"status":"default"}],"meta":{"pagination":{"cursor":"cursor-older"},"stream":{"id":"stream-current"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    val loaded = repository.loadMoreLogsFromApi()
    assertTrue("The older page should load", loaded)
    assertEquals(listOf("newer.example", "older.example"), repository.logs.value.map { it.domain })
    assertEquals("Duplicate records across cursor pages must be removed", 2, repository.logs.value.map { it.id }.distinct().size)
    assertTrue(requests.any { it.contains("cursor=cursor-older") })
    assertFalse(repository.logsPaginationState.value.hasMore)
  }

  @Test fun rawLogSnapshotPreservesIdenticalQueriesWithoutApiRecordIds() = runBlocking {
    val row = """{"domain":"repeat.example","timestamp":"2026-10-20T00:00:00.123Z","status":"default","protocol":"UDP"}"""
    response = { json("""{"data":[$row,$row],"meta":{"pagination":{"cursor":null}}}""") }

    assertTrue(repository.refreshLogsFromApi())

    assertEquals("raw=1 must retain every query even when API rows have identical fields", 2, repository.logs.value.size)
    assertEquals(2, repository.logs.value.map { it.id }.distinct().size)
  }

  @Test fun refreshingLogsPreservesPreviouslyLoadedOlderPages() = runBlocking {
    response = { request ->
      if (request.requestUrl?.queryParameter("cursor") == "cursor-older") {
        json("""{"data":[{"domain":"older.example","timestamp":"2026-10-19T23:00:00Z","status":"default"}],"meta":{"pagination":{"cursor":null}}}""")
      } else {
        json("""{"data":[{"domain":"newer.example","timestamp":"2026-10-20T00:00:00Z","status":"default"}],"meta":{"pagination":{"cursor":"cursor-older"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    assertTrue(repository.loadMoreLogsFromApi())
    assertTrue(repository.refreshLogsFromApi())

    assertEquals(listOf("newer.example", "older.example"), repository.logs.value.map { it.domain })
  }

  @Test fun deviceLogFilterUsesTheNextDnsDeviceIdAndUnidentifiedSentinel() = runBlocking {
    response = { request ->
      val query = request.requestUrl?.queryParameter("device")
      val row = if (query == "__UNIDENTIFIED__") {
        """{"domain":"unidentified.example","timestamp":"2026-10-20T00:00:00Z","status":"default"}"""
      } else {
        """{"domain":"Annem.example","timestamp":"2026-10-20T00:00:00Z","device":{"id":"device-a","name":"Annem"},"status":"default"}"""
      }
      json("""{"data":[$row],"meta":{"pagination":{"cursor":null}}}""")
    }

    assertTrue(repository.setLogsDeviceFilter("device-a"))
    assertTrue(requests.last().contains("device=device-a"))
    assertEquals("device-a", repository.logs.value.single().deviceId)
    assertTrue(repository.logDevices.value.any { it.id == "device-a" && it.name == "Annem" })

    assertTrue(repository.setLogsDeviceFilter("__UNIDENTIFIED__"))
    assertTrue(requests.last().contains("device=__UNIDENTIFIED__"))
    assertEquals("unidentified.example", repository.logs.value.single().domain)
    assertNull(repository.logs.value.single().deviceId)
  }

  @Test fun logStatusesAndApiReasonsRemainFaithfulToTheResponse() = runBlocking {
    response = {
      json("""{"data":[
        {"domain":"blocked.example","status":"blocked","reasons":[{"id":"blocklist:test","name":"Example blocklist"}]},
        {"domain":"allowed.example","status":"allowed"},
        {"domain":"default.example","status":"default"},
        {"domain":"error.example","status":"error"}
      ]}""")
    }

    assertTrue(repository.refreshLogsFromApi())
    val byDomain = repository.logs.value.associateBy { it.domain }
    assertEquals("blocked", byDomain.getValue("blocked.example").status)
    assertEquals("Example blocklist", byDomain.getValue("blocked.example").blockReason)
    assertTrue(byDomain.getValue("blocked.example").blocked)
    assertEquals("allowed", byDomain.getValue("allowed.example").status)
    assertEquals("default", byDomain.getValue("default.example").status)
    assertEquals("error", byDomain.getValue("error.example").status)
    assertFalse(byDomain.getValue("error.example").blocked)
  }

  @Test fun unknownLogReasonKeepsTheExactApiValueInsteadOfInventingALabel() {
    assertEquals("Vendor:Future-Rule", repository.formatBlockReason("Vendor:Future-Rule", null))
    assertEquals("Future server reason", repository.formatBlockReason("vendor:future-rule", "Future server reason"))
  }

  @Test fun statusFilterUsesDocumentedValuesAndRequestsANewFirstPage() = runBlocking {
    response = { request ->
      val status = request.requestUrl?.queryParameter("status")
      val cursor = request.requestUrl?.queryParameter("cursor")
      when {
        cursor == "blocked-next" -> json("""{"data":[{"domain":"blocked-older.example","status":"blocked"}],"meta":{"pagination":{"cursor":null}}}""")
        status == "blocked" -> json("""{"data":[{"domain":"blocked.example","status":"blocked"}],"meta":{"pagination":{"cursor":"blocked-next"}}}""")
        status == "allowed" -> json("""{"data":[{"domain":"allowed.example","status":"allowed"}],"meta":{"pagination":{"cursor":null}}}""")
        else -> json("""{"data":[{"domain":"default.example","status":"default"}],"meta":{"pagination":{"cursor":"all-next"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    assertTrue(repository.setLogsStatusFilter("blocked"))
    assertTrue(requests.last().contains("status=blocked"))
    assertEquals(listOf("blocked.example"), repository.logs.value.map { it.domain })
    assertTrue(repository.loadMoreLogsFromApi())
    assertTrue(requests.last().contains("status=blocked") && requests.last().contains("cursor=blocked-next"))
    assertEquals(listOf("blocked.example", "blocked-older.example"), repository.logs.value.map { it.domain })

    assertTrue(repository.setLogsStatusFilter("allowed"))
    assertTrue(requests.last().contains("status=allowed"))
    assertFalse("Changing status must start at page one", requests.last().contains("cursor="))
    assertEquals(listOf("allowed.example"), repository.logs.value.map { it.domain })
  }

  @Test fun searchFilterUsesDocumentedServerQueryAndStartsAtFirstPage() = runBlocking {
    response = { request ->
      val search = request.requestUrl?.queryParameter("search")
      val cursor = request.requestUrl?.queryParameter("cursor")
      when {
        cursor == "search-next" -> json("""{"data":[{"domain":"annem-old.example","status":"default"}]}""")
        search == "annem" -> json("""{"data":[{"domain":"annem.example","status":"default"}],"meta":{"pagination":{"cursor":"search-next"}}}""")
        else -> json("""{"data":[{"domain":"other.example","status":"default"}],"meta":{"pagination":{"cursor":"all-next"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    assertTrue(repository.loadMoreLogsFromApi())
    assertTrue(repository.setLogsSearchFilter(" annem "))
    assertTrue(requests.last().contains("search=annem"))
    assertFalse("Changing search must discard the old cursor", requests.last().contains("cursor="))
    assertEquals(listOf("annem.example"), repository.logs.value.map { it.domain })
    assertTrue(repository.loadMoreLogsFromApi())
    assertTrue(requests.last().contains("search=annem") && requests.last().contains("cursor=search-next"))
    assertEquals(listOf("annem.example", "annem-old.example"), repository.logs.value.map { it.domain })
  }

  @Test fun changingSearchFilterRejectsAnOlderInFlightPage() = runBlocking {
    val entered = gate(); val release = gate()
    response = { request ->
      when {
        request.requestUrl?.queryParameter("cursor") == "stale-search-next" -> {
          entered.countDown()
          await(release)
          json("""{"data":[{"domain":"stale.example","status":"default"}]}""")
        }
        request.requestUrl?.queryParameter("search") == "annem" ->
          json("""{"data":[{"domain":"annem.example","status":"default"}]}""")
        else ->
          json("""{"data":[{"domain":"current.example","status":"default"}],"meta":{"pagination":{"cursor":"stale-search-next"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    val pendingOlderPage = async(Dispatchers.IO) { repository.loadMoreLogsFromApi() }
    await(entered)
    assertTrue(repository.setLogsSearchFilter("annem"))
    release.countDown()

    assertFalse("A page for the previous search must be discarded", pendingOlderPage.await())
    assertEquals(listOf("annem.example"), repository.logs.value.map { it.domain })
  }

  @Test fun changingStatusFilterRejectsAnOlderInFlightPage() = runBlocking {
    val entered = gate(); val release = gate()
    response = { request ->
      when {
        request.requestUrl?.queryParameter("cursor") == "stale-next" -> {
          entered.countDown()
          await(release)
          json("""{"data":[{"domain":"stale.example","status":"default"}]}""")
        }
        request.requestUrl?.queryParameter("status") == "blocked" ->
          json("""{"data":[{"domain":"blocked.example","status":"blocked"}],"meta":{"pagination":{"cursor":null}}}""")
        else ->
          json("""{"data":[{"domain":"current.example","status":"default"}],"meta":{"pagination":{"cursor":"stale-next"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    val pendingOlderPage = async(Dispatchers.IO) { repository.loadMoreLogsFromApi() }
    await(entered)
    assertTrue(repository.setLogsStatusFilter("blocked"))
    release.countDown()

    assertFalse("A page for the previous status must be discarded", pendingOlderPage.await())
    assertEquals(listOf("blocked.example"), repository.logs.value.map { it.domain })
  }

  @Test fun fullSyncDoesNotRequestAnAccountEndpointThatIsNotInThePublicApi() = runBlocking {
    response = { json("""{"data":[]}""") }

    val sectionResults = repository.loadActiveProfileDataFromApi()

    assertFalse("Full sync must not call the undocumented /account route", requests.any { it.contains("/account") })
    assertEquals("Every sync result must correspond to a supported profile section", SyncSection.values().toSet(), sectionResults.keys)
    assertFalse("Account connection state is local app state, not an API sync section", sectionResults.keys.any { it.name == "ACCOUNT" })
  }

  @Test fun fullSyncPreservesCursorPagesAlreadyLoadedFromTheLogsScreen() = runBlocking {
    response = { request ->
      if (request.requestUrl?.encodedPath?.endsWith("/logs") == true &&
        request.requestUrl?.queryParameter("cursor") == "cursor-older") {
        json("""{"data":[{"domain":"older.example","timestamp":"2026-10-19T23:00:00Z","status":"default"}],"meta":{"pagination":{"cursor":null}}}""")
      } else if (request.requestUrl?.encodedPath?.endsWith("/logs") == true) {
        json("""{"data":[{"domain":"newer.example","timestamp":"2026-10-20T00:00:00Z","status":"default"}],"meta":{"pagination":{"cursor":"cursor-older"},"stream":{"id":"stream-current"}}}""")
      } else {
        json("""{"data":[]}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    assertTrue(repository.loadMoreLogsFromApi())
    repository.loadActiveProfileDataFromApi()

    assertEquals(listOf("newer.example", "older.example"), repository.logs.value.map { it.domain })
  }

  @Test fun changingDeviceFilterRejectsAnOlderInFlightPage() = runBlocking {
    val entered = gate(); val release = gate()
    response = { request ->
      if (request.requestUrl?.queryParameter("cursor") == "cursor-older") {
        entered.countDown()
        await(release)
        json("""{"data":[{"domain":"stale.example","timestamp":"2026-10-19T00:00:00Z","device":{"id":"device-a","name":"Annem"}}]}""")
      } else if (request.requestUrl?.queryParameter("device") == "device-b") {
        json("""{"data":[{"domain":"current.example","timestamp":"2026-10-20T00:00:00Z","device":{"id":"device-b","name":"Phone"}}],"meta":{"pagination":{"cursor":"cursor-b"}}}""")
      } else {
        json("""{"data":[{"domain":"latest.example","timestamp":"2026-10-20T01:00:00Z","device":{"id":"device-a","name":"Annem"}}],"meta":{"pagination":{"cursor":"cursor-older"}}}""")
      }
    }

    assertTrue(repository.refreshLogsFromApi())
    val pendingOlderPage = async(Dispatchers.IO) { repository.loadMoreLogsFromApi() }
    await(entered)
    assertTrue(repository.setLogsDeviceFilter("device-b"))
    release.countDown()

    assertFalse("A page for the previous filter must be discarded", pendingOlderPage.await())
    assertEquals(listOf("current.example"), repository.logs.value.map { it.domain })
    assertTrue(repository.logs.value.all { it.deviceId == "device-b" })
  }

  @Test fun lateCreatedProfileCannotRestorePreferencesAfterLogout() = runBlocking {
    val entered = gate(); val release = gate()
    response = { req ->
      if (req.method == "POST") json("""{"data":{"id":"cccccc"}}""")
      else { entered.countDown(); await(release); json("""{"data":{"id":"cccccc","name":"Created"}}""") }
    }
    val pending = async(Dispatchers.IO) { repository.createProfileRemote("Created") }
    await(entered)
    assertTrue(repository.logout().isSuccess)
    release.countDown()
    assertCancelled(pending)
    assertTrue(repository.profiles.value.isEmpty())
    assertEquals("", preferences.apiKey)
    assertEquals("", preferences.activeProfileId)
    assertNull(preferences.getProfiles())
  }

  @Test fun mutationForACannotBeVerifiedUsingB() = runBlocking {
    val entered = gate(); val release = gate()
    response = { req ->
      if (req.method == "PATCH") { entered.countDown(); await(release); json("{}") }
      else json("""{"data":{"web3":true}}""")
    }
    val pending = async(Dispatchers.IO) { repository.setWeb3(true) }
    await(entered)
    repository.setActiveProfile("bbbbbb")
    release.countDown()
    assertCancelled(pending)
    assertFalse(requests.any { it.startsWith("GET /profiles/bbbbbb/") })
    assertFalse(repository.configSettings.value.web3)
  }

  @Test fun successfulMutationReadsAndCachesTheSameProfile() = runBlocking {
    response = { req -> if (req.method == "PATCH") json("{}") else json("""{"data":{"web3":true}}""") }
    assertTrue(repository.setWeb3(true).isSuccess)
    assertEquals(listOf("PATCH /profiles/aaaaaa/settings", "GET /profiles/aaaaaa/settings"), requests.toList())
    assertTrue(repository.configSettings.value.web3)
    assertEquals(true, preferences.getConfigSettings("aaaaaa")?.web3)
  }

  @Test fun web3TrueFalseAndMissingFieldPreserveAuthoritativeState() = runBlocking {
    response = { json("""{"data":{"web3":true}}""") }
    assertTrue(repository.refreshSection(SyncSection.SETTINGS))
    assertTrue(repository.configSettings.value.web3)
    response = { json("""{"data":{}}""") }
    repository.refreshSection(SyncSection.SETTINGS)
    assertTrue(repository.configSettings.value.web3)
    response = { json("""{"data":{"web3":false}}""") }
    repository.refreshSection(SyncSection.SETTINGS)
    assertFalse(repository.configSettings.value.web3)
    repository.loadLocalProfileData("aaaaaa")
    assertFalse(repository.configSettings.value.web3)
  }

  @Test fun diagnosticTimeoutDoesNotReplaceLastSuccessWithFreshSuccess() = runBlocking {
    var answer: NextDnsTestResponse? = NextDnsTestResponse(status = "ok", profile = "aaaaaa", protocol = "DOT")
    repository = newRepository { answer }
    val success = repository.runDiagnosticTest("aaaaaa")
    answer = null
    val failure = repository.runDiagnosticTest("aaaaaa")
    assertEquals("error", failure.status)
    assertNotNull(failure.errorMessage)
    assertEquals(success.lastTestedTime, failure.lastTestedTime)
    assertFalse(failure.isTesting)
  }

  @Test fun cancellingRefreshClearsItsBusyState() = runBlocking {
    val entered = gate(); val release = gate()
    response = { entered.countDown(); await(release); json("""{"data":{}}""") }
    val pending = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SETTINGS) }
    await(entered)
    assertTrue(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isRefreshing)
    pending.cancelAndJoin()
    release.countDown()
    assertFalse(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isRefreshing)
  }

  @Test fun malformedExportLinkReturnsFailureInsteadOfThrowing() = runBlocking {
    response = { json("""{"data":{"url":"https://"}}""") }
    assertTrue(repository.exportLogs(ByteArrayOutputStream()).isFailure)
  }

  @Test fun successfulHttpReadDoesNotVerifyAnUnappliedMutation() = runBlocking {
    response = { req -> if (req.method == "PATCH") json("{}") else json("""{"data":{"web3":false}}""") }
    assertTrue(repository.setWeb3(true).isFailure)
    assertFalse(repository.configSettings.value.web3)
    assertFalse(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isSaving)
  }

  @Test fun obsoleteSettingsCannotWriteAnotherProfilesCache() = runBlocking {
    val entered = gate(); val release = gate()
    response = { entered.countDown(); await(release); json("""{"data":{"web3":true}}""") }
    val pending = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SETTINGS) }
    await(entered)
    repository.setActiveProfile("bbbbbb")
    release.countDown()
    assertCancelled(pending)
    assertNull(preferences.getConfigSettings("aaaaaa"))
    assertNull(preferences.getConfigSettings("bbbbbb"))
    assertFalse(repository.configSettings.value.web3)
  }

  @Test fun oldRefreshCancellationCannotClearNewRefreshFlag() = runBlocking {
    val firstEntered = gate(); val secondEntered = gate()
    val firstRelease = gate(); val secondRelease = gate()
    val count = java.util.concurrent.atomic.AtomicInteger()
    response = {
      if (count.incrementAndGet() == 1) { firstEntered.countDown(); await(firstRelease) }
      else { secondEntered.countDown(); await(secondRelease) }
      json("""{"data":{"web3":true}}""")
    }
    val first = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SETTINGS) }
    await(firstEntered)
    val second = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SETTINGS) }
    await(secondEntered)
    first.cancelAndJoin()
    firstRelease.countDown()
    assertTrue(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isRefreshing)
    secondRelease.countDown()
    assertTrue(second.await())
    assertFalse(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isRefreshing)
  }

  @Test fun differentSectionRefreshesKeepBothFlagsAndResults() = runBlocking {
    val settingsEntered = gate(); val securityEntered = gate(); val release = gate()
    response = { request ->
      if (request.requestUrl!!.encodedPath.endsWith("settings")) settingsEntered.countDown() else securityEntered.countDown()
      await(release)
      json("""{"data":{"web3":true,"ddns":true}}""")
    }
    val settings = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SETTINGS) }
    val security = async(Dispatchers.IO) { repository.refreshSection(SyncSection.SECURITY) }
    await(settingsEntered); await(securityEntered)
    assertTrue(repository.sectionSyncStates.value.getValue(SyncSection.SETTINGS).isRefreshing)
    assertTrue(repository.sectionSyncStates.value.getValue(SyncSection.SECURITY).isRefreshing)
    release.countDown()
    assertTrue(settings.await()); assertTrue(security.await())
    assertTrue(repository.configSettings.value.web3)
    assertTrue(repository.securitySettings.value.ddns)
  }

  @Test fun normalCreatedProfileIsVerifiedPersistedAndSelected() = runBlocking {
    response = { req -> if (req.method == "POST") json("""{"data":{"id":"cccccc"}}""")
      else json("""{"data":{"id":"cccccc","name":"Created"}}""") }
    assertTrue(repository.createProfileRemote("Created").isSuccess)
    assertEquals("cccccc", repository.activeProfileId.value)
    assertEquals("cccccc", preferences.activeProfileId)
    assertEquals(3, preferences.getProfiles()?.size)
  }

  @Test fun cancellingFullSyncClearsBusyStateWithoutStartingAnalytics() = runBlocking {
    val entered = gate(); val release = gate()
    response = { entered.countDown(); await(release); json("{}", 404) }
    val pending = async(Dispatchers.IO) { repository.loadActiveProfileDataFromApi() }
    await(entered)
    assertTrue(repository.isSyncing.value)
    pending.cancelAndJoin()
    release.countDown()
    assertFalse(repository.isSyncing.value)
    assertFalse(requests.any { it.contains("/analytics/") })
  }

  @Test fun deletingSelectedProfileVerifiesRemovalBeforeSelectingReplacement() = runBlocking {
    response = { req -> if (req.method == "DELETE") json("{}")
      else json("""{"data":[{"id":"bbbbbb","name":"B"}]}""") }
    assertTrue(repository.deleteProfileRemote("aaaaaa").isSuccess)
    assertEquals("bbbbbb", repository.activeProfileId.value)
    assertEquals("bbbbbb", preferences.activeProfileId)
    assertEquals(listOf("bbbbbb"), preferences.getProfiles()?.map { it.id })
    assertTrue(requests.contains("DELETE /profiles/aaaaaa"))
    assertTrue(requests.contains("GET /profiles"))
  }

  @Test fun deletingLastProfileVerifiesEmptyListAndClearsSelection() = runBlocking {
    response = { req -> if (req.method == "DELETE") json("{}") else json("""{"data":[]}""") }
    assertTrue(repository.deleteProfileRemote("aaaaaa").isSuccess)
    assertEquals("", repository.activeProfileId.value)
    assertEquals("", preferences.activeProfileId)
    assertTrue(preferences.getProfiles().orEmpty().isEmpty())
    assertFalse(repository.isSyncing.value)
  }

  @Test fun acceptedDeleteDoesNotSucceedWhenServerStillContainsTarget() = runBlocking {
    response = { req -> if (req.method == "DELETE") json("{}")
      else json("""{"data":[{"id":"aaaaaa","name":"A"},{"id":"bbbbbb","name":"B"}]}""") }
    assertTrue(repository.deleteProfileRemote("aaaaaa").isFailure)
    assertEquals("aaaaaa", repository.activeProfileId.value)
    assertEquals("aaaaaa", preferences.activeProfileId)
  }

  @Test fun cancelledDiagnosticClearsBothRepositoryAndViewModelBusyFlags() = runBlocking {
    val entered = gate()
    repository = newRepository { entered.countDown(); awaitCancellation() }
    val viewModel = NextDnsViewModel(repository)
    val pending = async(Dispatchers.IO) { viewModel.refreshDiagnostic() }
    await(entered)
    assertTrue(viewModel.isDiagnosticRunning.value)
    pending.cancelAndJoin()
    assertFalse(repository.testResult.value.isTesting)
    assertFalse(viewModel.isDiagnosticRunning.value)
  }

  @Test fun providerFailureIsReportedByViewModelWithoutSuccess() = runBlocking {
    val viewModel = NextDnsViewModel(repository)
    assertTrue(viewModel.exportLogsToDocument { throw SecurityException("Fixture denied") }.isFailure)
    assertEquals(true, viewModel.uiMessage.value?.isError)
  }

  @Test fun validExportUsesNoApiKeyAndOutputWriteFailureReturnsFailure() = runBlocking {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      assertNull(chain.request().header("X-Api-Key"))
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        .code(200).message("Fixture").body("csv".toResponseBody()).build()
    }.build()
    repository = NextDnsRepository(preferences, apiService = api, diagnosticService = testApi,
      diagnosticProbe = { null }, downloadClient = client, backgroundWorkEnabled = false)
    response = { json("""{"data":{"url":"https://export.fixture.invalid/logs.csv"}}""") }
    val output = ByteArrayOutputStream()
    assertTrue(repository.exportLogs(output).isSuccess)
    assertEquals("csv", output.toString("UTF-8"))
    assertTrue(repository.exportLogs(object : OutputStream() {
      override fun write(value: Int) { throw IOException("Fixture disk full") }
    }).isFailure)
  }

  @Test fun stoppingRepositorySseCancelsTheActualNetworkCall() = runBlocking {
    val headers = gate(); val released = gate()
    val activeCall = java.util.concurrent.atomic.AtomicReference<Call>()
    val client = OkHttpClient.Builder()
      .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url("/stream")).build()) }
      .eventListener(object : EventListener() {
        override fun responseHeadersEnd(call: Call, response: Response) { activeCall.set(call); headers.countDown() }
        override fun connectionReleased(call: Call, connection: okhttp3.Connection) { released.countDown() }
      }).build()
    repository = NextDnsRepository(preferences, apiService = api, diagnosticService = testApi,
      diagnosticProbe = { null }, streamClient = client, backgroundWorkEnabled = false)
    response = { request ->
      if (request.requestUrl!!.encodedPath == "/stream") MockResponse().setBody(":" + "x".repeat(1_000))
        .throttleBody(1, 1, TimeUnit.SECONDS)
      else json("""{"data":[],"meta":{"stream":{"id":"fixture-stream"}}}""")
    }
    repository.startLogsStream()
    await(headers)
    repository.stopLogsStream()
    // Cancellation can finish as callEnd when the response closes before another read.
    // Verify resource release and the Call's actual cancellation state in either case.
    await(released)
    assertTrue(activeCall.get().isCanceled())
    assertFalse(repository.isLiveStreaming.value)
  }

  @Test fun liveLogStreamRetainsOnlyTheMostRecentThousandEntries() = runBlocking {
    val streamBodyEnded = CountDownLatch(1)
    val client = OkHttpClient.Builder()
      .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url("/stream")).build()) }
      .eventListener(object : EventListener() {
        override fun responseBodyEnd(call: Call, byteCount: Long) {
          if (call.request().header("Accept") == "text/event-stream") streamBodyEnded.countDown()
        }
      }).build()
    repository = NextDnsRepository(preferences, apiService = api, diagnosticService = testApi,
      diagnosticProbe = { null }, streamClient = client, backgroundWorkEnabled = false)
    val streamBody = buildString {
      repeat(1_100) { index ->
        val seconds = (index % 60).toString().padStart(2, '0')
        append("id: stream-$index\n")
        append("data: {\"timestamp\":\"2026-10-20T00:00:$seconds.${index}Z\",\"domain\":\"host-$index.example\",\"status\":\"default\"}\n\n")
      }
    }
    response = { request ->
      if (request.requestUrl!!.encodedPath == "/stream") MockResponse().setBody(streamBody)
      else json("""{"data":[],"meta":{"stream":{"id":"fixture-stream"}}}""")
    }

    repository.startLogsStream()
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while ((streamBodyEnded.count > 0 || repository.logs.value.firstOrNull()?.domain != "host-1099.example") &&
        System.nanoTime() < deadline) {
        // Each SSE event switches to Dispatchers.Main before it can be consumed.
        // Pump Robolectric's main looper instead of blocking it on CountDownLatch.await.
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(1)
      }
      assertTrue("SSE response should finish", streamBodyEnded.count == 0L)
      assertEquals(1_000, repository.logs.value.size)
      assertEquals("host-1099.example", repository.logs.value.firstOrNull()?.domain)
      assertTrue(repository.logsPaginationState.value.retentionLimitReached)
    } finally {
      repository.stopLogsStream()
    }
  }

  @Test fun liveLogStreamKeepsIdenticalQueriesWithDifferentEventCursors() = runBlocking {
    val streamBodyEnded = CountDownLatch(1)
    val client = OkHttpClient.Builder()
      .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url("/stream")).build()) }
      .eventListener(object : EventListener() {
        override fun responseBodyEnd(call: Call, byteCount: Long) {
          if (call.request().header("Accept") == "text/event-stream") streamBodyEnded.countDown()
        }
      }).build()
    repository = NextDnsRepository(preferences, apiService = api, diagnosticService = testApi,
      diagnosticProbe = { null }, streamClient = client, backgroundWorkEnabled = false)
    val event = """{"timestamp":"2026-10-20T00:00:00.123Z","domain":"repeat.example","status":"default","protocol":"UDP"}"""
    val streamBody = "id: event-one\ndata: $event\n\nid: event-two\ndata: $event\n\n"
    response = { request ->
      if (request.requestUrl!!.encodedPath == "/stream") MockResponse().setBody(streamBody)
      else json("""{"data":[],"meta":{"stream":{"id":"snapshot-cursor"}}}""")
    }

    repository.startLogsStream()
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while ((streamBodyEnded.count > 0 || repository.logs.value.size < 2) && System.nanoTime() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(1)
      }
      assertEquals("Each SSE cursor identifies a separate raw query", 2, repository.logs.value.size)
      assertEquals(setOf("event-one", "event-two"), repository.logs.value.map { it.id }.toSet())
    } finally {
      repository.stopLogsStream()
    }
  }

  companion object {
    private fun json(body: String, code: Int = 200) = MockResponse()
      .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
  }
}
