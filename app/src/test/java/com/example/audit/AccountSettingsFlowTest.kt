package com.example.audit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.model.AllowDenyItem
import com.example.data.model.NativeTrackingEntry
import com.example.data.model.PrivacySettings
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.connectedProfileStatusText
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AllowlistScreen
import com.example.ui.screens.DenylistScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NavTab
import com.example.ui.viewmodel.NextDnsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h2400dp-mdpi", application = NextDnsApp::class)
class AccountSettingsFlowTest : AuditFixture() {
  @get:Rule val rule = createComposeRule()

  private fun english() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
  }

  @Test fun settingsExposeServiceLegalAppPreferenceAndDeleteGroupsOnce() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SettingsScreen(model) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_f5e0e67ae0)).assertDoesNotExist()

    val settingsList = rule.onNodeWithTag("settings_scroll_list")
    listOf(
      "settings_section_service",
      "settings_section_legal",
      "settings_section_app_preferences",
      "settings_section_delete"
    ).forEach { tag ->
      settingsList.performScrollToNode(hasTestTag(tag))
      rule.onNodeWithTag(tag).assertIsDisplayed()
    }
    settingsList.performScrollToNode(hasTestTag("settings_section_app_preferences"))
    val preferencesTop = rule.onNodeWithTag("settings_section_app_preferences")
      .fetchSemanticsNode().boundsInRoot.top
    val legalTop = rule.onNodeWithTag("settings_section_legal")
      .fetchSemanticsNode().boundsInRoot.top
    assertTrue("Legal documents should follow app-local preferences at the end of settings", preferencesTop < legalTop)
    settingsList.performScrollToNode(hasTestTag("legal_terms_link"))
    rule.onNodeWithTag("legal_terms_link").assertIsDisplayed()
    rule.onNodeWithTag("legal_privacy_link").assertExists()
    assertEquals(1, rule.onAllNodesWithTag("legal_terms_link").fetchSemanticsNodes().size)
    assertEquals(1, rule.onAllNodesWithTag("legal_privacy_link").fetchSemanticsNodes().size)
  }

  @Test fun loginDoesNotScatterLegalLinksOutsideTheGroupedWelcomeFlow() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { LoginScreen(model) } }

    rule.onNodeWithTag("legal_terms_link").assertDoesNotExist()
    rule.onNodeWithTag("legal_privacy_link").assertDoesNotExist()
  }

  @Test fun accountSectionsPutConnectionAndProfilesBeforeMetricsAndSync() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }

    val keyTop = rule.onNodeWithText(AppStrings.get(R.string.ui_f9b1cde612))
      .fetchSemanticsNode().boundsInRoot.top
    val profilesTop = rule.onNodeWithText("Profiles")
      .fetchSemanticsNode().boundsInRoot.top
    rule.onNodeWithText("Profiles (1)").assertDoesNotExist()
    assertEquals("API connected • 1 profile", AppStrings.get(R.string.account_status_connected, 1))
    rule.onNodeWithText(AppStrings.get(R.string.ui_f5e0e67ae0)).assertIsDisplayed()
    val metricsTop = rule.onNodeWithText(AppStrings.get(R.string.ui_c636e5398b))
      .fetchSemanticsNode().boundsInRoot.top
    val syncTop = rule.onNodeWithText(AppStrings.get(R.string.ui_8b96719687))
      .fetchSemanticsNode().boundsInRoot.top

    assertTrue("Account status and key should appear before profiles", keyTop < profilesTop)
    assertTrue("Profiles should appear before usage metrics", profilesTop < metricsTop)
    assertTrue("Usage metrics should appear before the full-sync action", metricsTop < syncTop)
  }

  @Test fun connectedProfileStatusUsesLocalizedSingularAndPluralCounts() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val expected = listOf(
      Triple("en", "API connected • 1 profile", "API connected • 2 profiles"),
      Triple("tr", "API bağlı • 1 profil", "API bağlı • 2 profil"),
      Triple("de", "API verbunden • 1 Profil", "API verbunden • 2 Profile"),
      Triple("fr", "API connectée • 1 profil", "API connectée • 2 profils"),
      Triple("es", "API conectada • 1 perfil", "API conectada • 2 perfiles")
    )

    expected.forEach { (locale, singular, plural) ->
      LocalePreferences.setSelection(context, locale)
      assertEquals("$locale singular", singular, connectedProfileStatusText(1))
      assertEquals("$locale plural", plural, connectedProfileStatusText(2))
    }
    english()
  }

  @Test fun connectionAreaOpensFromDedicatedTopRightButton() {
    english()
    response = { request ->
      if (request.path == "/") {
        json("""{"status":"ok","protocol":"SYNTHETIC_DIAGNOSTIC_PROTOCOL","profile":"fp0123456789abcdef","server":"lhr"}""")
      } else json("{}", 500)
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { HomeScreen(model) } }

    rule.onNodeWithTag("home_nextdns_connection_button")
      .assertIsDisplayed()
      .assertHasClickAction()
      .assertWidthIsEqualTo(48.dp)
      .assertHeightIsEqualTo(48.dp)
      .performClick()

    rule.onNodeWithTag("account_connection_status").assertIsDisplayed()
    rule.onNodeWithText("Profiles").assertIsDisplayed()
    rule.onNodeWithText("Profiles (1)").assertDoesNotExist()
    rule.onNodeWithTag("tab_account").assertDoesNotExist()

    rule.waitUntil(10_000) {
      rule.onAllNodes(hasText("SYNTHETIC_DIAGNOSTIC_PROTOCOL", substring = true))
        .fetchSemanticsNodes().isNotEmpty()
    }

    rule.onNodeWithTag("tab_setup").performScrollTo().performClick().assertIsSelected()
  }

  @Test fun localTrackingProtectionIconUsesTheSmallerSize() {
    english()
    preferences.savePrivacySettings(
      "aaaaaa",
      PrivacySettings(nativeTracking = listOf(NativeTrackingEntry(id = "windows", name = "Windows", active = true)))
    )
    repository.loadLocalProfileData("aaaaaa")
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { PrivacyScreen(model) } }

    rule.onNodeWithTag("native_tracking_icon_windows").assertWidthIsEqualTo(24.dp)
    rule.onNodeWithTag("native_tracking_icon_windows").assertHeightIsEqualTo(24.dp)
  }

  @Test fun allowAndDenyListFaviconsUseCompactSize() {
    english()
    preferences.saveAllowlist("aaaaaa", listOf(AllowDenyItem(id = "allowed", domain = "allowed.example")))
    preferences.saveDenylist("aaaaaa", listOf(AllowDenyItem(id = "blocked", domain = "blocked.example")))
    repository.loadLocalProfileData("aaaaaa")
    val model = NextDnsViewModel(repository)
    val showAllowlist = mutableStateOf(true)
    rule.setContent {
      AppTheme {
        if (showAllowlist.value) AllowlistScreen(model) else DenylistScreen(model)
      }
    }

    rule.onNodeWithTag("allowlist_domain_icon").assertWidthIsEqualTo(16.dp)
    rule.onNodeWithTag("allowlist_domain_icon").assertHeightIsEqualTo(16.dp)
    rule.runOnIdle { showAllowlist.value = false }
    rule.onNodeWithTag("denylist_domain_icon").assertWidthIsEqualTo(16.dp)
    rule.onNodeWithTag("denylist_domain_icon").assertHeightIsEqualTo(16.dp)
  }

  @Test fun destinationsMapShowsCountryDataWhenAnotherAnalyticsRequestFails() {
    english()
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/destinations") &&
          request.requestUrl?.queryParameter("type") == "countries" ->
          json("""{"data":[{"code":"US","domains":["dns.example"],"queries":42}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/queryTypes") -> json("{}", 500)
        else -> json("""{"data":[]}""")
      }
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AnalyticsScreen(model) } }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }

    assertTrue("The countries endpoint should be called: ${requests.joinToString()}", requests.any {
      it.contains("/analytics/destinations?") && it.contains("type=countries")
    })
    assertEquals("The API country data should reach analytics state", listOf("US"), model.analytics.value.topCountries.map { it.code })
    rule.waitUntil(30_000) {
      rule.onAllNodesWithTag("world_map_loading").fetchSemanticsNodes().isEmpty() &&
        rule.onAllNodesWithTag("world_map_unavailable").fetchSemanticsNodes().isEmpty()
    }
    rule.onNodeWithTag("world_country_map").assertExists().performTouchInput {
      click(androidx.compose.ui.geometry.Offset(74f, 60f))
    }
    rule.onNodeWithTag("world_map_selected_country").assertTextContains("United States")
    rule.onNodeWithTag("world_map_selected_queries").assertTextContains("42 queries")
    rule.onNodeWithText("dns.example").assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.analytics_partial_failure_notice)).assertIsDisplayed()
  }

  @Test fun partialAnalyticsWarningIsVisibleInAccountOverview() {
    english()
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/status") ->
          json("""{"data":[{"status":"default","queries":100}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/queryTypes") -> json("{}", 500)
        request.requestUrl?.encodedPath.orEmpty().contains("/analytics/") -> json("""{"data":[]}""")
        else -> json("{}", 500)
      }
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }
    rule.runOnIdle { model.refreshAnalytics(null, null) }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }
    rule.onNodeWithTag("account_analytics_warning")
      .performScrollTo()
      .assertTextEquals(AppStrings.get(R.string.analytics_partial_failure_notice))
    rule.onNodeWithText("100").assertExists()
  }

  @Test fun fullRefreshReadsNextDnsStateWithoutPostingLocalPreferences() {
    english()
    response = { json("{}", 500) }
    val model = NextDnsViewModel(repository)
    model.syncAllData()

    rule.waitUntil(30_000) { model.fullSyncStatus.value !is com.example.data.repository.FullSyncStatus.Running && model.fullSyncStatus.value != null }

    assertTrue("Refreshing server state must never mutate NextDNS settings", requests.isNotEmpty() && requests.all { it.startsWith("GET ") })
    assertTrue("App connection and diagnostic state must not be synced as an API section", requests.none {
      it.contains("/account", ignoreCase = true) || it.contains("/connection", ignoreCase = true)
    })
  }

  @Test fun gafamChartAndAccessibleDescriptionContainAllSixSegments() {
    english()
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/status") ->
          json("""{"data":[{"status":"default","queries":100}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/destinations") &&
          request.requestUrl?.queryParameter("type") == "gafam" ->
          json("""{"data":[{"company":"Google","queries":10},{"company":"Facebook","queries":20},{"company":"Microsoft","queries":30},{"company":"Apple","queries":20},{"company":"Amazon","queries":10}]}""")
        request.requestUrl?.encodedPath.orEmpty().contains("/analytics/") -> json("""{"data":[]}""")
        else -> json("{}", 404)
      }
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AnalyticsScreen(model) } }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }
    val chart = rule.onNodeWithTag("gafam_chart").performScrollTo()
    chart.assertContentDescriptionContains("Google", substring = true)
    chart.assertContentDescriptionContains("Apple", substring = true)
    chart.assertContentDescriptionContains("Amazon", substring = true)
    assertTrue("GAFAM analytics must use the documented destinations?type=gafam endpoint", requests.any {
      it.contains("/analytics/destinations?") && it.contains("type=gafam")
    })
  }

  @Test fun analyticsDevicePickerDisplaysModelFallbackAndFiltersByApiId() {
    english()
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/status") ->
          json("""{"data":[{"status":"default","queries":10}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/devices") ->
          json("""{"data":[{"id":"device-a","name":"undefined","model":"Pixel 9","queries":10}]}""")
        request.requestUrl?.encodedPath.orEmpty().contains("/analytics/") -> json("""{"data":[]}""")
        else -> json("{}", 404)
      }
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AnalyticsScreen(model) } }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }
    rule.onNodeWithTag("analytics_device_filter").performClick()
    rule.onNodeWithTag("analytics_device_option_device-a").assertTextContains("Pixel 9")
    rule.onNodeWithTag("analytics_device_option_device-a").performClick()
    rule.waitUntil(30_000) { requests.any { it.contains("device=device-a") } }
    assertTrue(requests.any { it.contains("device=device-a") })
    rule.onNodeWithText("undefined", substring = true).assertDoesNotExist()
  }

  @Test fun accountDiagnosticDoesNotTreatAnUnmatchedProfileAsConnected() {
    english()
    response = { request ->
      if (request.path == "/") {
        json("""{"status":"ok","protocol":"DOH","profile":"fpfedcba9876543210","server":"lhr"}""")
      } else json("{}", 500)
    }
    runBlocking { repository.runDiagnosticTest("aaaaaa") }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }

    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_unverified_title)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.ui_8c25d923c8)).assertDoesNotExist()
  }

  @Test fun diagnosticShowsOnlyTheServerResolverAndAnycastFieldsReturnedByNextDns() {
    english()
    response = { request ->
      if (request.path == "/") {
        json("""{"status":"ok","protocol":"DOH","profile":"fp0123456789abcdef","server":"iad","resolver":"45.90.28.0","anycast":true}""")
      } else json("{}", 500)
    }
    runBlocking { repository.runDiagnosticTest("aaaaaa") }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }

    rule.onNodeWithText("Server: iad", substring = true).assertIsDisplayed()
    rule.onNodeWithText("Resolver: 45.90.28.0").assertIsDisplayed()
    rule.onNodeWithText("Anycast: enabled").assertIsDisplayed()
    rule.onNodeWithTag("diagnostic_source").assertTextEquals(AppStrings.get(R.string.diagnostic_source))
    rule.onNodeWithTag("diagnostic_checked_at").assertIsDisplayed()
    rule.onNodeWithText("New York", substring = true).assertDoesNotExist()
    rule.onNodeWithText("United States", substring = true).assertDoesNotExist()
  }

  @Test fun fullSyncFailureMessageNamesTheSectionsThatFailed() {
    english()
    response = { json("{}", 500) }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }

    model.syncAllData()
    rule.waitUntil(30_000) { model.uiMessage.value?.isError == true }

    assertTrue(
      "The sync result should identify at least one failed section",
      model.uiMessage.value!!.text.contains(AppStrings.get(R.string.ui_bde6632ed8))
    )
  }

  @Test fun fullSyncShowsLogAndAnalyticsFailuresSeparatelyFromRemoteSettings() {
    english()
    response = { request ->
      val path = request.requestUrl?.encodedPath.orEmpty()
      if (path.endsWith("/logs") || path.contains("/analytics/")) json("{}", 500)
      else json("""{"data":[]}""")
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AccountScreen(model) } }

    model.syncAllData()
    rule.waitUntil(30_000) {
      model.fullSyncStatus.value is com.example.data.repository.FullSyncStatus.Completed &&
        model.uiMessage.value?.text?.contains(NavTab.LOGS.title) == true &&
        model.uiMessage.value?.text?.contains(NavTab.ANALYTICS.title) == true
    }

    rule.onNodeWithTag("full_sync_logs_label")
      .performScrollTo()
      .assertTextEquals(NavTab.LOGS.title)
    rule.onNodeWithTag("full_sync_analytics_label")
      .performScrollTo()
      .assertTextEquals(NavTab.ANALYTICS.title)
    rule.onNodeWithTag("full_sync_logs_status")
      .assertTextEquals(AppStrings.get(R.string.sync_section_failed))
    rule.onNodeWithTag("full_sync_analytics_status")
      .assertTextEquals(AppStrings.get(R.string.sync_section_failed))
    val failureMessage = model.uiMessage.value?.text.orEmpty()
    assertTrue(failureMessage.contains(NavTab.LOGS.title))
    assertTrue(failureMessage.contains(NavTab.ANALYTICS.title))
  }
}
