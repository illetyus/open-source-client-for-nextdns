package com.example.audit

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.model.AllowDenyItem
import com.example.data.model.NativeTrackingEntry
import com.example.data.model.PrivacySettings
import com.example.data.repository.TldCatalogLoadState
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.connectedProfileStatusText
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AllowlistScreen
import com.example.ui.screens.DenylistScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.TldCatalogContent
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NavTab
import com.example.ui.viewmodel.NextDnsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h2400dp-mdpi", application = NextDnsApp::class)
class AccountSettingsFlowTest : AuditFixture() {
  @get:Rule val rule = AuditComposeTestRule()

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
    rule.onNodeWithTag("settings_connection_entry").assertDoesNotExist()
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

  @Test fun accountShortcutAndSeparateConnectionPageAreRemoved() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { HomeScreen(model) } }

    rule.onNodeWithTag("home_account_menu_button").assertDoesNotExist()
    rule.onNodeWithTag("home_account_menu_connection").assertDoesNotExist()
    rule.onNodeWithTag("settings_connection_page").assertDoesNotExist()
    rule.onNodeWithTag("tab_setup").assertIsSelected()
  }

  @Test fun apiConnectionControlsStayInlineInSettings() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SettingsScreen(model) } }

    rule.onNodeWithTag("settings_api_connection").assertIsDisplayed()
    rule.onNodeWithTag("settings_connection_page").assertDoesNotExist()
  }

  @Test fun networkDiagnosticsAreShownBelowTheSetupStatusCard() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SetupScreen(model) } }

    val statusTop = rule.onNodeWithTag("setup_connection_status_banner")
      .fetchSemanticsNode().boundsInRoot.top
    val diagnosticsTop = rule.onNodeWithTag("setup_network_diagnostics")
      .fetchSemanticsNode().boundsInRoot.top

    assertTrue("Network diagnostics should follow the setup connection status", statusTop < diagnosticsTop)
  }

  @Test fun bypassAgeVerificationIsShownWithNextDnsServiceSettings() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SettingsScreen(model) } }

    val settingsList = rule.onNodeWithTag("settings_scroll_list")
    settingsList.performScrollToNode(hasTestTag("settings_bypass_age_verification"))
    rule.onNodeWithTag("settings_bypass_age_verification").assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.bypass_age_verification_title)).assertIsDisplayed()
  }

  @Test fun loginDoesNotScatterLegalLinksOutsideTheGroupedWelcomeFlow() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { LoginScreen(model) } }

    rule.onNodeWithTag("legal_terms_link").assertDoesNotExist()
    rule.onNodeWithTag("legal_privacy_link").assertDoesNotExist()
  }

  @Test fun setupDiagnosticButtonRunsANewDiagnosticRequest() {
    english()
    response = { request ->
      if (request.path == "/") {
        json("""{"status":"ok","protocol":"SYNTHETIC_DIAGNOSTIC_PROTOCOL","profile":"fp0123456789abcdef","server":"lhr"}""")
      } else json("{}", 500)
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SetupScreen(model) } }

    rule.onNodeWithTag("setup_run_diagnostic")
      .assertHasClickAction()
      .performClick()
    rule.waitUntil(10_000) { model.testResult.value.lastTestedTime > 0L }

    assertTrue(requests.any { it == "GET /" || it.endsWith(" GET /") })
    rule.onNodeWithTag("setup_network_diagnostics").performScrollTo()
    rule.waitUntil(5_000) {
      rule.onAllNodesWithTag("setup_diagnostic_server")
        .fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag("setup_diagnostic_server")
      .assertTextContains("SYNTHETIC_DIAGNOSTIC_PROTOCOL", substring = true)
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

    rule.onNodeWithTag("allowlist_domain_icon").assertWidthIsEqualTo(12.dp)
    rule.onNodeWithTag("allowlist_domain_icon").assertHeightIsEqualTo(12.dp)
    rule.runOnIdle { showAllowlist.value = false }
    rule.onNodeWithTag("denylist_domain_icon").assertWidthIsEqualTo(12.dp)
    rule.onNodeWithTag("denylist_domain_icon").assertHeightIsEqualTo(12.dp)
  }

  @Test fun tldCatalogContentShowsLoadingInsteadOfCatalogEntriesBeforeLoadCompletes() {
    english()
    rule.setContent {
      AppTheme {
        TldCatalogContent(
          loadState = TldCatalogLoadState.LOADING,
          tlds = listOf(".work", ".zip"),
          onAddTld = {}
        )
      }
    }
    rule.onNodeWithTag("security_tld_catalog_loading").assertIsDisplayed()
    rule.onNodeWithText(".work").assertDoesNotExist()
    rule.onNodeWithText(".zip").assertDoesNotExist()
  }

  @Test fun publishedNextDnsSecurityFieldsAppearInTheSecurityScreen() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SecurityScreen(model) } }

    val securityList = rule.onNodeWithTag("security_settings_list")
    listOf(
      R.string.ui_9c19e93b43,
      R.string.ui_b8c859e123,
      R.string.ui_b6b6e376c7,
      R.string.ui_309e669643,
      R.string.ui_7a41105eb3,
      R.string.ui_9904d77eb3,
      R.string.ui_6194b3ff35,
      R.string.ui_fa2b128b73,
      R.string.ui_c3cd82c4f0,
      R.string.ui_3399d9a672,
      R.string.ui_b8b2a448eb,
      R.string.ui_bfa7376292,
      R.string.ui_122c713cab
    ).forEach { title ->
      val titleText = AppStrings.get(title)
      securityList.performScrollToNode(hasText(titleText))
      rule.onNodeWithText(titleText).assertExists()
    }
  }

  @Test fun observedNextDnsSecurityFieldsRenderAsSwitches() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SecurityScreen(model) } }

    val observedFields = listOf(
      "dataDropServices" to R.string.security_data_drop_services_title,
      "decentralizedWebGateways" to R.string.security_decentralized_web_gateways_title,
      "dnsDataExfiltration" to R.string.security_dns_data_exfiltration_title,
      "dnsPayloadDelivery" to R.string.security_dns_payload_delivery_title,
      "fastFluxNetworks" to R.string.security_fast_flux_networks_title,
      "freeHostingDomains" to R.string.security_free_hosting_domains_title,
      "highRiskTlds" to R.string.security_high_risk_tlds_title,
      "newlyActiveDomains" to R.string.security_newly_active_domains_title,
      "residentialHosting" to R.string.security_residential_hosting_title,
      "tunnelingEndpoints" to R.string.security_tunneling_endpoints_title,
      "untrustedCertificates" to R.string.security_untrusted_certificates_title
    )

    observedFields.forEach { (feature, titleId) ->
      val title = AppStrings.get(titleId)
      rule.onNodeWithTag("security_settings_list").performScrollToNode(hasText(title))
      rule.onNodeWithTag("security_flag_$feature", useUnmergedTree = true)
        .assert(
          hasAnyDescendant(
            SemanticsMatcher.expectValue(
              androidx.compose.ui.semantics.SemanticsProperties.Role,
              androidx.compose.ui.semantics.Role.Switch
            )
          )
        )
    }
  }

  @Test fun successfulEmptyTldCatalogIsNotReportedAsANetworkFailure() = runBlocking {
    response = { json("""{"data":[]}""") }
    repository.loadAllLiveCatalogs()

    assertEquals(TldCatalogLoadState.EMPTY, repository.tldCatalogLoadState.value)
    assertTrue(repository.availableTldsCatalog.value.isEmpty())
  }

  @Test fun loadedTldCatalogUsesOnlyServerProvidedEntries() = runBlocking {
    response = { request ->
      if (request.requestUrl?.encodedPath == "/security/tlds") {
        json("""{"data":[{"id":"zip","spamhaus":0}]}""")
      } else {
        json("""{"data":[]}""")
      }
    }
    repository.loadAllLiveCatalogs()

    assertEquals(TldCatalogLoadState.AVAILABLE, repository.tldCatalogLoadState.value)
    assertEquals(listOf("zip"), repository.availableTldsCatalog.value.map { it.id })
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

  @Test fun analyticsScreenDisplaysIpsReturnedByTheExistingAnalyticsEndpoint() {
    english()
    response = { request ->
      when {
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/ips") ->
          json("""{"data":[{"ip":"203.0.113.45","queries":17,"network":{"isp":"Example ISP","asn":64500},"geo":{"countryCode":"TR","country":"Türkiye","city":"Ankara"}}]}""")
        request.requestUrl?.encodedPath.orEmpty().endsWith("/analytics/domains") &&
          request.requestUrl?.queryParameter("status") == "default" ->
          json("""{"data":[{"domain":"allowed-icons.example","queries":8}]}""")
        request.requestUrl?.encodedPath.orEmpty().contains("/analytics/") -> json("""{"data":[]}""")
        else -> json("{}", 404)
      }
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AnalyticsScreen(model) } }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }

    assertEquals(listOf("203.0.113.45"), model.analytics.value.topIps.map { it.ip })
    assertTrue("The existing IP analytics endpoint should be requested", requests.any {
      it.contains("/analytics/ips?")
    })
    rule.onNodeWithTag("analytics_resolved_domains_card").performScrollTo()
    rule.onNodeWithTag("analytics_domain_favicon", useUnmergedTree = true).assertExists()
    rule.onNodeWithTag("analytics_ips_card").performScrollTo().assertIsDisplayed()
    rule.onNodeWithText("203.0.113.45").assertIsDisplayed()
    rule.onNodeWithText("Ankara, Türkiye").assertIsDisplayed()
    rule.onNodeWithText("Example ISP · AS64500").assertIsDisplayed()
  }

  @Test fun partialAnalyticsWarningIsVisibleInAnalytics() {
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
    rule.setContent { AppTheme { AnalyticsScreen(model) } }
    rule.runOnIdle { model.refreshAnalytics(null, null) }

    rule.waitUntil(30_000) {
      model.analyticsLastSuccessAt.value != null && !model.isAnalyticsLoading.value
    }
    rule.onNodeWithTag("analytics_partial_warning")
      .performScrollTo()
      .assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.analytics_partial_failure_notice)).assertIsDisplayed()
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

  @Test fun setupDiagnosticDoesNotTreatAnUnmatchedProfileAsConnected() {
    english()
    response = { request ->
      if (request.path == "/") {
        json("""{"status":"ok","protocol":"DOH","profile":"fpfedcba9876543210","server":"lhr"}""")
      } else json("{}", 500)
    }
    runBlocking { repository.runDiagnosticTest("aaaaaa") }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { SetupScreen(model) } }

    rule.onNodeWithTag("setup_network_diagnostics").performScrollTo()
    val expectedStatus = AppStrings.get(R.string.diagnostic_unverified_title)
    rule.waitUntil(5_000) {
      runCatching {
        rule.onNodeWithTag("setup_diagnostic_status_label").assertTextEquals(expectedStatus)
      }.isSuccess
    }
    rule.onNodeWithTag("setup_diagnostic_status_label").assertTextEquals(expectedStatus)
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
    rule.setContent { AppTheme { SetupScreen(model) } }

    rule.onNodeWithTag("setup_network_diagnostics").performScrollTo()
    rule.onNodeWithText("Server: iad", substring = true).assertIsDisplayed()
    rule.onNodeWithText("Resolver: 45.90.28.0").assertIsDisplayed()
    rule.onNodeWithText("Anycast: enabled").assertIsDisplayed()
    rule.onNodeWithTag("diagnostic_source").assertTextEquals(AppStrings.get(R.string.diagnostic_source))
    rule.onNodeWithTag("diagnostic_checked_at").assertIsDisplayed()
    rule.onNodeWithTag("diagnostic_region_unavailable").assertIsDisplayed()
    rule.onNodeWithText("New York", substring = true).assertDoesNotExist()
    rule.onNodeWithText("United States", substring = true).assertDoesNotExist()
  }

  @Test fun fullSyncFailureMessageNamesTheSectionsThatFailed() {
    english()
    response = { json("{}", 500) }
    val model = NextDnsViewModel(repository)
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
    model.syncAllData()
    rule.waitUntil(30_000) {
      model.fullSyncStatus.value is com.example.data.repository.FullSyncStatus.Completed &&
        model.uiMessage.value?.text?.contains(NavTab.LOGS.title) == true &&
        model.uiMessage.value?.text?.contains(NavTab.ANALYTICS.title) == true
    }

    val completed = model.fullSyncStatus.value as com.example.data.repository.FullSyncStatus.Completed
    assertFalse("The logs area should be reported as failed", completed.result.logsRefreshed)
    assertEquals(com.example.data.repository.SyncAreaOutcome.FAILED, completed.result.analyticsOutcome)
    val failureMessage = model.uiMessage.value?.text.orEmpty()
    assertTrue(failureMessage.contains(NavTab.LOGS.title))
    assertTrue(failureMessage.contains(NavTab.ANALYTICS.title))
  }

  @Test fun fullSyncReportsPartialAnalyticsWhileKeepingSuccessfulPanels() {
    english()
    response = { request ->
      val path = request.requestUrl?.encodedPath.orEmpty()
      if (path.endsWith("/analytics/destinations") &&
        request.requestUrl?.queryParameter("type") == "countries") {
        json("{}", 500)
      } else if (path.endsWith("/analytics/status")) {
        json("""{"data":[{"status":"default","queries":123,"blocked":12}]}""")
      } else {
        json("""{"data":[]}""")
      }
    }
    val model = NextDnsViewModel(repository)
    model.syncAllData()
    rule.waitUntil(30_000) {
      model.fullSyncStatus.value is com.example.data.repository.FullSyncStatus.Completed &&
        model.uiMessage.value?.isError == true
    }

    val result = (model.fullSyncStatus.value as com.example.data.repository.FullSyncStatus.Completed).result
    assertFalse("A partially refreshed analytics area must not count as fully refreshed", result.analyticsRefreshed)
    assertEquals(com.example.data.repository.SyncAreaOutcome.PARTIAL, result.analyticsOutcome)
    assertFalse("Partial analytics must prevent an all-areas success", result.allAreasSucceeded)
    assertEquals("Successful analytics panels should still be committed", 123L, repository.analytics.value.totalQueries)
    assertTrue(model.uiMessage.value?.text?.contains(NavTab.ANALYTICS.title) == true)
  }
}


