package com.example.data.notifications

import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.NextDnsApp
import com.example.data.api.NextDnsApiService
import com.example.data.api.NextDnsTestService
import com.example.data.local.NextDnsPreferences
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.api.NextDnsNetworkClient
import com.example.data.repository.NextDnsRepository
import com.example.data.repository.ApiConnectionStatus
import com.example.data.security.ApiKeyProtector
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS", application = NextDnsApp::class)
class NotificationAccountBoundaryTest {
  private class FixtureProtector : ApiKeyProtector {
    override fun encrypt(plainText: String) = "fixture:$plainText"
    override fun decrypt(envelope: String) = envelope.removePrefix("fixture:")
  }

  private lateinit var context: Context
  private lateinit var preferences: NextDnsPreferences
  private lateinit var repository: NextDnsRepository
  private lateinit var manager: NotificationManager
  private lateinit var client: OkHttpClient
  private lateinit var api: NextDnsApiService
  private var loginCode = 200
  @Volatile private var profileList =
    """{"data":[{"id":"aaaaaa","name":"A"},{"id":"bbbbbb","name":"B"}]}"""

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    preferences = NextDnsPreferences(context, FixtureProtector())
    preferences.clear()
    preferences.apiKey = "fixture-account"
    preferences.activeProfileId = "aaaaaa"
    runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    manager = context.getSystemService(NotificationManager::class.java)
    manager.cancelAll()
    NotificationCenter.createChannels(context)
    NotificationCenter.activateAccountNotifications()
    check(LegalAcceptanceStore(context).acceptCurrentTerms())
    client = OkHttpClient.Builder().addInterceptor { chain ->
      val request = chain.request()
      val (code, body) = when (request.method to request.url.encodedPath) {
        "GET" to "/profiles" -> loginCode to if (loginCode == 200) profileList else "{}"
        "POST" to "/profiles" -> 200 to """{"data":{"id":"cccccc"}}"""
        "GET" to "/profiles/cccccc" -> 200 to """{"data":{"id":"cccccc","name":"Created"}}"""
        "DELETE" to "/profiles/aaaaaa" -> 200 to "{}"
        else -> 404 to "{}"
      }
      Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(code).message("Fixture").body(body.toResponseBody()).build()
    }.build()
    api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
      .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi))
      .build().create(NextDnsApiService::class.java)
    repository = newRepository()
  }

  private fun newRepository() = NextDnsRepository(
      preferences = preferences,
      apiService = api,
      diagnosticService = unusedService(),
      diagnosticProbe = { null },
      downloadClient = client,
      streamClient = client,
      backgroundWorkEnabled = false,
      catalogCallFactory = client
    )

  @After fun tearDown() {
    manager.cancelAll()
    preferences.clear()
  }

  @Test fun guestEntryRemovesPreviouslyPostedAccountSummary() {
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    assertTrue(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })

    repository.suspendForGuest()

    assertFalse(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })
  }

  @Test fun profileSwitchRemovesPreviouslyPostedAccountSummary() {
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    assertTrue(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })

    repository.setActiveProfile("bbbbbb")

    assertFalse(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })
  }

  @Test fun signOutRemovesPreviouslyPostedAccountSummary() = runBlocking {
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    assertTrue(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })

    assertTrue(repository.logout().isSuccess)

    assertFalse(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })
  }

  @Test fun returningToSameProfileRejectsOldWorkerAndAllowsCurrentWorker() {
    val old = requireNotNull(NotificationCenter.captureAccountGeneration())
    repository.setActiveProfile("bbbbbb")
    repository.setActiveProfile("aaaaaa")
    assertFalse(NotificationCenter.postDailySummaryForAccount(context, 123L, 45L, old) { true })
    assertFalse(NotificationCenter.postConfigChangedForAccount(context, old) { true })
    assertTrue(manager.activeNotifications.isEmpty())
    val current = requireNotNull(NotificationCenter.captureAccountGeneration())
    assertTrue(NotificationCenter.postDailySummaryForAccount(context, 456L, 12L, current) { true })
    val shown = manager.activeNotifications.single { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY }
    assertEquals(Notification.VISIBILITY_PRIVATE, shown.notification.visibility)
    val publicText = requireNotNull(shown.notification.publicVersion).extras
      .getCharSequence(Notification.EXTRA_TEXT).toString()
    assertFalse(publicText.contains("123") || publicText.contains("45"))
  }

  @Test fun staleWorkerCannotOverwriteNotificationStateAfterProfileRoundTrip() = runBlocking {
    val oldGeneration = requireNotNull(NotificationCenter.captureAccountGeneration())
    val obsolete = NotificationPreferences.forAccount(context, oldGeneration)
    repository.setActiveProfile("bbbbbb")
    repository.setActiveProfile("aaaaaa")
    val current = NotificationPreferences.forAccount(
      context, requireNotNull(NotificationCenter.captureAccountGeneration())
    )
    current.establishConfigBaseline("aaaaaa", "current-digest")
    current.markSummaryDay("2026-11-18")
    obsolete.establishConfigBaseline("aaaaaa", "stale-digest")
    obsolete.markSummaryDay("2026-11-19")
    assertEquals("current-digest", current.configState("aaaaaa").observedDigest)
    assertEquals("2026-11-18", current.lastSummaryDay())
  }

  @Test fun guestEntryDisablesPostingWhileKeepingSavedLogin() {
    val old = requireNotNull(NotificationCenter.captureAccountGeneration())
    repository.suspendForGuest()
    assertEquals("fixture-account", preferences.apiKey)
    assertNull(NotificationCenter.captureAccountGeneration())
    assertFalse(NotificationCenter.postDailySummaryForAccount(context, 123L, 45L, old) { true })
  }

  @Test fun guestChoiceSurvivesRepositoryRecreationUntilLoginSucceeds() = runBlocking {
    repository.suspendForGuest()
    val reopened = newRepository()
    assertTrue(reopened.wasGuestModeSelected())
    assertEquals("", reopened.apiKey.value)
    assertEquals("", reopened.activeProfileId.value)
    loginCode = 401
    assertTrue(reopened.loginWithApiKey("wrong-key").isFailure)
    assertTrue(reopened.wasGuestModeSelected())
    assertNull(NotificationCenter.captureAccountGeneration())
    loginCode = 200
    assertTrue(reopened.loginWithApiKey("fixture-account").isSuccess)
    assertFalse(reopened.wasGuestModeSelected())
    assertTrue(NotificationCenter.captureAccountGeneration() != null)
  }

  @Test fun validatingSameAccountAndProfileKeepsExistingNotifications() = runBlocking {
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    assertTrue(repository.loginWithApiKey("fixture-account", restoreProfileId = "aaaaaa").isSuccess)
    assertTrue(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })
  }

  @Test fun failedReplacementKeepsCurrentAccountNotifications() = runBlocking {
    assertTrue(repository.loginWithApiKey("fixture-account").isSuccess)
    val generation = requireNotNull(NotificationCenter.captureAccountGeneration())
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    loginCode = 401
    assertTrue(repository.loginWithApiKey("rejected-account").isFailure)
    assertTrue(repository.apiStatus.value is ApiConnectionStatus.Connected)
    assertEquals(generation, NotificationCenter.captureAccountGeneration())
    assertTrue(manager.activeNotifications.any { it.id == NotificationCenter.NOTIFICATION_DAILY_SUMMARY })
  }

  @Test fun rejectionOfCurrentCredentialRemovesNotifications() = runBlocking {
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    loginCode = 401
    assertTrue(repository.loginWithApiKey("fixture-account").isFailure)
    assertNull(NotificationCenter.captureAccountGeneration())
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test fun replacingAccountClearsNotificationsEvenWithSameProfileId() = runBlocking {
    val generation = requireNotNull(NotificationCenter.captureAccountGeneration())
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    assertTrue(repository.loginWithApiKey("replacement-account").isSuccess)
    assertEquals("aaaaaa", preferences.activeProfileId)
    assertFalse(NotificationCenter.postDailySummaryForAccount(context, 123L, 45L, generation) { true })
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test fun replacingAccountClearsStoredNotificationPreferences() = runBlocking {
    val notificationPrefs = NotificationPreferences(context)
    notificationPrefs.setConfigChangeAlertsEnabled(true)
    notificationPrefs.setDailySummaryEnabled(true)
    notificationPrefs.markSummaryDay("2026-11-26")

    assertTrue(repository.loginWithApiKey("replacement-account").isSuccess)

    val after = notificationPrefs.currentSettings()
    assertFalse(after.configChangeAlertsEnabled)
    assertFalse(after.dailySummaryEnabled)
    assertNull(notificationPrefs.lastSummaryDay())
  }

  @Test fun revalidatingSameAccountPreservesStoredNotificationPreferences() = runBlocking {
    val notificationPrefs = NotificationPreferences(context)
    notificationPrefs.setConfigChangeAlertsEnabled(true)
    notificationPrefs.markSummaryDay("2026-11-26")

    assertTrue(repository.loginWithApiKey("fixture-account").isSuccess)

    val after = notificationPrefs.currentSettings()
    assertTrue(after.configChangeAlertsEnabled)
    assertEquals("2026-11-26", notificationPrefs.lastSummaryDay())
  }

  @Test fun newlyCreatedProfileReplacesTheNotificationOwner() = runBlocking {
    val old = requireNotNull(NotificationCenter.captureAccountGeneration())
    assertTrue(NotificationCenter.postConfigChanged(context))
    assertTrue(repository.createProfileRemote("Created").isSuccess)
    assertEquals("cccccc", preferences.activeProfileId)
    assertFalse(NotificationCenter.postConfigChangedForAccount(context, old) { true })
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test fun deletingSelectedProfileReplacesTheNotificationOwner() = runBlocking {
    val old = requireNotNull(NotificationCenter.captureAccountGeneration())
    assertTrue(NotificationCenter.postDailySummary(context, 123L, 45L))
    profileList = """{"data":[{"id":"bbbbbb","name":"B"}]}"""
    assertTrue(repository.deleteProfileRemote("aaaaaa").isSuccess)
    assertEquals("bbbbbb", preferences.activeProfileId)
    assertFalse(NotificationCenter.postDailySummaryForAccount(context, 123L, 45L, old) { true })
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T> unusedService(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java)
  ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as T
}
