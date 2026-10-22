package com.example

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.example.data.legal.LegalAcceptanceStore
import com.example.ui.screens.formatRelativeTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run only against a disposable test installation; never enter a real API key. */
@RunWith(AndroidJUnit4::class)
class NativeLoginNavigationTest {
  @get:Rule val rule = createEmptyComposeRule()

  @Test fun freshInstallRequiresExplicitTermsBeforeGuestNavigation() {
    // The instrumentation APK's desugared runtime also serves target-app code.
    // Exercise all ISO parsers used by production logs, so L8 retains those
    // methods in release tests and a missing API fails before guest navigation.
    val epoch = 1609459200000L
    val utc = "2021-01-01T00:00:00Z"
    val offset = "2021-01-01T01:00:00+01:00"
    val local = "2021-01-01T00:00:00"
    assertEquals(epoch, java.time.Instant.parse(utc).toEpochMilli())
    assertEquals(epoch, java.time.OffsetDateTime.parse(offset).toInstant().toEpochMilli())
    assertEquals(epoch, java.time.LocalDateTime.parse(local).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
    for (timestamp in listOf(utc, offset, local)) {
      assertEquals(formatRelativeTime(epoch.toString()), formatRelativeTime(timestamp))
    }
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertTrue(context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit())
    assertTrue(context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit())
    val acceptance = LegalAcceptanceStore(context)
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      rule.onNodeWithTag("welcome_accept_checkbox").assertIsOff()
      rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
      rule.onAllNodesWithTag("login_guest").assertCountEquals(0)
      rule.onNodeWithTag("welcome_privacy").performScrollTo().performClick()
      rule.runOnIdle { assertFalse(acceptance.isAccepted()) }
      rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
      rule.onNodeWithTag("welcome_terms").performScrollTo().performClick()
      rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().performClick()
      rule.onNodeWithTag("welcome_continue").performScrollTo().performClick()
      rule.waitUntil(20_000) { rule.onAllNodesWithTag("login_guest").fetchSemanticsNodes().isNotEmpty() }
      rule.runOnIdle { assertTrue(acceptance.isAccepted()) }
      rule.onNodeWithTag("login_submit").assertIsNotEnabled()
      scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
      rule.onNodeWithTag("login_guest").performScrollTo().performClick()
      val settingsTags = listOf("tab_settings", "rail_settings", "drawer_settings")
      rule.waitUntil(20_000) { settingsTags.any { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() } }
      val settingsTag = settingsTags.first { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
      rule.onNodeWithTag(settingsTag).performScrollTo().performClick()
      // The legal links live below the fold in a LazyColumn item, so the link
      // is not in the semantics tree until the parent scrolls that item in.
      rule.onNodeWithTag("settings_scroll_list")
        .performScrollToNode(hasTestTag("legal_privacy_link"))
      rule.onNodeWithTag("legal_privacy_link").performClick()
      rule.waitUntil(20_000) {
        rule.onAllNodesWithTag("legal_document_content", useUnmergedTree = true)
          .fetchSemanticsNodes().isNotEmpty()
      }
      rule.onNodeWithTag("legal_document_content", useUnmergedTree = true)
        .assertTextContains("privacy-2026-10-DRAFT-2", substring = true)
      rule.onNodeWithTag("legal_reader_close").performClick()
      rule.runOnIdle { assertTrue(acceptance.isAccepted()) }
    }
  }
}
