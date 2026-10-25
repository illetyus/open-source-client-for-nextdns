package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.legal.LegalAcceptanceStore
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NavTab
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Duration
import java.time.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-device UI journeys. Fixtures and disposable application storage only. */
@RunWith(AndroidJUnit4::class)
class NativeScreenJourneyTest {
  @get:Rule val rule = createEmptyComposeRule()
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private var scenario: ActivityScenario<MainActivity>? = null
  private var fixture: ScreenJourneyFixture? = null
  private val modelStore = ViewModelStore()

  @Before fun resetDisposableInstallation() {
    assertTrue(context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit())
    assertTrue(context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit())
    LocalePreferences.setSelection(context, "en")
  }

  @After fun closeJourney() {
    scenario?.close()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { modelStore.clear() }
    fixture?.close()
  }

  private fun awaitTag(tag: String) {
    rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun navigate(tab: NavTab) {
    val name = tab.name.lowercase()
    val tags = listOf("tab_$name", "rail_$name", "drawer_$name")
    rule.waitUntil(20_000) { tags.any { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() } }
    val tag = tags.first { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
    val target = rule.onNodeWithTag(tag)
    target.performScrollTo().assertIsDisplayed().performClick()
    awaitTag("screen_$name")
    rule.onNodeWithTag("screen_$name").assertIsDisplayed()
    rule.onNodeWithTag(tag).assertIsSelected()
  }

  private fun enterGuest() {
    scenario = ActivityScenario.launch(MainActivity::class.java)
    rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
    rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().performClick()
    rule.onNodeWithTag("welcome_continue").performScrollTo().performClick()
    awaitTag("login_guest")
    rule.onNodeWithTag("login_submit").assertIsNotEnabled()
    rule.onNodeWithTag("login_guest").performScrollTo().performClick()
    awaitTag("screen_setup")
  }

  private fun connected(): ScreenJourneyFixture {
    assertTrue(LegalAcceptanceStore(context).acceptCurrentTerms())
    // Launch before constructing isolated credentials; the activity's normal repository stays logged out.
    scenario = ActivityScenario.launch(MainActivity::class.java)
    val backend = ScreenJourneyFixture(context).also { fixture = it }
    InstrumentationRegistry.getInstrumentation().runOnMainSync { modelStore.put("journey", backend.model) }
    scenario!!.onActivity { activity ->
      activity.setContent { AppTheme { HomeScreen(backend.model) } }
    }
    awaitTag("screen_setup")
    return backend
  }

  private fun openAndCloseLegalReaders() {
    val list = rule.onNodeWithTag("settings_scroll_list")
    for (tag in listOf("legal_terms_link", "legal_privacy_link")) {
      list.performScrollToNode(hasTestTag(tag))
      rule.onNodeWithTag(tag).performClick()
      awaitTag("legal_document_content")
      rule.onNodeWithTag("legal_document_content").assertIsDisplayed()
      rule.onNodeWithTag("legal_reader_close").performClick()
      rule.onNodeWithTag("legal_reader_close").assertDoesNotExist()
    }
  }

  @Test fun freshGuestCanVisitAllNineDestinationsAndReadLegalDocuments() {
    enterGuest()
    NavTab.entries.forEach(::navigate)
    openAndCloseLegalReaders()
    assertTrue(LegalAcceptanceStore(context).isAccepted())
    assertTrue(NextDnsApp.preferences.apiKey.isBlank())
  }

  @Test fun guestNavigationSurvivesRecreationAndLandscape() {
    enterGuest()
    navigate(NavTab.SETTINGS)
    scenario!!.recreate()
    awaitTag("screen_settings")
    scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    rule.waitUntil(20_000) {
      rule.onAllNodesWithTag("rail_setup").fetchSemanticsNodes().isNotEmpty() ||
        rule.onAllNodesWithTag("drawer_setup").fetchSemanticsNodes().isNotEmpty()
    }
    NavTab.entries.forEach(::navigate)
    scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    awaitTag("tab_settings")
    navigate(NavTab.SETUP)
  }

  @Test fun connectedScreensRemainNavigableAndSettingsGroupsAreReachable() {
    val backend = connected()
    NavTab.entries.forEach(::navigate)
    val list = rule.onNodeWithTag("settings_scroll_list")
    listOf("settings_section_service", "settings_section_app_preferences", "settings_section_legal", "settings_section_delete").forEach { tag ->
      list.performScrollToNode(hasTestTag(tag))
      rule.onNodeWithTag(tag).assertIsDisplayed()
    }
    openAndCloseLegalReaders()
    rule.waitUntil(20_000) { backend.calls.any { it.path.endsWith("/analytics/status") } }
    assertTrue(backend.calls.any { it.path.endsWith("/logs") })
    assertTrue(NextDnsApp.preferences.apiKey.isBlank())
  }

  @Test fun rejectedSecurityMutationKeepsConfirmedValueAndCanBeRetried() {
    val backend = connected()
    navigate(NavTab.SECURITY)
    val title = AppStrings.get(R.string.ui_b6b6e376c7)
    val list = rule.onNodeWithTag("security_settings_list")
    list.performScrollToNode(hasText(title))
    // The title's enclosing setting card contains exactly one switch.
    val toggle = rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag("security_google_safe_browsing")))
    toggle.assertIsOn()
    backend.rejectSecurityMutation = true
    toggle.performClick()
    rule.waitUntil(20_000) { backend.calls.any { it.method == "PATCH" && it.path.endsWith("/security") } }
    rule.waitUntil(20_000) { backend.model.sectionSyncStates.value.values.none { it.isSaving } }
    toggle.assertIsOn()
    assertTrue(backend.googleSafeBrowsing)
    backend.rejectSecurityMutation = false
    val patches = backend.calls.count { it.method == "PATCH" && it.path.endsWith("/security") }
    toggle.performClick()
    rule.waitUntil(20_000) {
      backend.calls.count { it.method == "PATCH" && it.path.endsWith("/security") } > patches && !backend.googleSafeBrowsing
    }
    rule.waitUntil(20_000) { !backend.model.securitySettings.value.googleSafeBrowsing }
    toggle.assertIsOff()
  }

  @Test fun domainListsRejectMalformedInputAndPersistValidRules() {
    val backend = connected()
    for ((tab, prefix) in listOf(NavTab.DENYLIST to "denylist", NavTab.ALLOWLIST to "allowlist")) {
      navigate(tab)
      val field = rule.onNodeWithTag("${prefix}_domain_input")
      field.performTextReplacement("invalid")
      androidx.test.espresso.Espresso.closeSoftKeyboard()
      val before = backend.calls.count { it.method == "POST" }
      rule.onNodeWithTag("${prefix}_add_domain").performClick()
      rule.onNodeWithText(AppStrings.get(R.string.ui_c10eecdcb0)).assertIsDisplayed()
      assertEquals(before, backend.calls.count { it.method == "POST" })
      val domain = "added-$prefix.fixture.example"
      field.performTextReplacement(domain)
      androidx.test.espresso.Espresso.closeSoftKeyboard()
      rule.onNodeWithTag("${prefix}_add_domain").performClick()
      rule.waitUntil(20_000) {
        backend.calls.any { it.method == "POST" && it.path.endsWith("/$prefix") && it.body.contains(domain) }
      }
      rule.waitUntil(20_000) {
        val rules = if (tab == NavTab.DENYLIST) backend.model.denylist.value else backend.model.allowlist.value
        rules.any { it.domain == domain }
      }
      field.assertTextEquals("")
      rule.onNodeWithText(domain).assertIsDisplayed()
    }
  }

  @Test fun privacyAndParentalCatalogDialogsOpenAndDismissWithoutMutations() {
    val backend = connected()
    val before = backend.calls.count { it.method in setOf("POST", "PATCH", "DELETE") }
    for ((tab, tag, caption) in listOf(
      Triple(NavTab.PRIVACY, "privacy_settings_list", R.string.manage_blocklists),
      Triple(NavTab.PRIVACY, "privacy_settings_list", R.string.ui_eaa29349bc),
      Triple(NavTab.PARENTAL, "parental_settings_list", R.string.ui_b02d56c853),
      Triple(NavTab.PARENTAL, "parental_settings_list", R.string.ui_c216d0fa85)
    )) {
      navigate(tab)
      val text = AppStrings.get(caption)
      rule.onNodeWithTag(tag).performScrollToNode(hasText(text, ignoreCase = true))
      rule.onNodeWithText(text, ignoreCase = true).performClick()
      rule.onNode(isDialog()).assertIsDisplayed()
      androidx.test.espresso.Espresso.pressBack()
      rule.onNode(isDialog()).assertDoesNotExist()
      rule.onNodeWithTag("screen_${tab.name.lowercase()}").assertIsDisplayed()
    }
    assertEquals(before, backend.calls.count { it.method in setOf("POST", "PATCH", "DELETE") })
  }

  @Test fun logAndAnalyticsFiltersChangeRequestsAndKeepNavigationUsable() {
    val backend = connected()
    navigate(NavTab.LOGS)
    rule.onNodeWithTag("logs_history_range_button").performClick()
    rule.onNodeWithTag("logs_history_option_7d").performClick()
    rule.onNodeWithTag("logs_status_blocked").performClick()
    rule.onNodeWithTag("logs_search_field").performTextReplacement("blocked.fixture")
    androidx.test.espresso.Espresso.closeSoftKeyboard()
    rule.waitUntil(20_000) {
      backend.calls.any { call ->
        if (!call.path.endsWith("/logs")) false else {
          val url = ("https://localhost${call.path}?${call.query}").toHttpUrl()
          val from = url.queryParameter("from")?.let(Instant::parse)
          val to = url.queryParameter("to")?.let(Instant::parse)
          from != null && to != null && Duration.between(from, to) == Duration.ofDays(7) &&
            url.queryParameter("status") == "blocked" && url.queryParameter("search") == "blocked.fixture"
        }
      }
    }
    rule.onNodeWithTag("logs_device_filter").performClick()
    awaitTag("logs_device_option_phone")
    rule.onNodeWithTag("logs_device_option_phone").performClick()
    rule.waitUntil(20_000) { backend.calls.any { it.path.endsWith("/logs") && it.query.contains("device=phone") } }
    navigate(NavTab.ANALYTICS)
    rule.onNodeWithTag("analytics_time_filter").performClick()
    rule.onNodeWithTag("analytics_time_option_7d").performClick()
    rule.onNodeWithTag("analytics_device_filter").performClick()
    awaitTag("analytics_device_option_phone")
    rule.onNodeWithTag("analytics_device_option_phone").performClick()
    rule.waitUntil(20_000) { backend.calls.any { it.path.endsWith("/analytics/status") && it.query.contains("device=phone") && it.query.contains("from=-7d") } }
    navigate(NavTab.SETUP)
    rule.onNodeWithTag("setup_connection_status_banner").assertIsDisplayed()
  }
}
