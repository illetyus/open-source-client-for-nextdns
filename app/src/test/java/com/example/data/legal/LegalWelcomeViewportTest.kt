package com.example.data.legal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.i18n.LocalePreferences
import com.example.ui.screens.LegalWelcomeScreen
import com.example.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w640dp-h320dp-land-mdpi", application = NextDnsApp::class)
class LegalWelcomeViewportTest {
  @get:Rule val rule = createComposeRule()

  @Test fun shortLandscapeAtLargeFontKeepsDocumentsAndConsentReachableInFiveLanguages() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val original = LocalePreferences.selection(context)
    val language = mutableStateOf("en")
    var attempts = 0
    assertTrue(LocalePreferences.setSelection(context, "en"))
    rule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
        key(language.value) {
          AppTheme { LegalWelcomeScreen(onAccept = { attempts++; false }) }
        }
      }
    }
    try {
      for (selected in LocalePreferences.supportedLanguages) {
        rule.runOnIdle {
          assertTrue(LocalePreferences.setSelection(context, selected))
          language.value = selected
        }
        rule.onNodeWithTag("welcome_document_viewport")
          .assertHeightIsAtLeast(160.dp).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("welcome_document_text").assertTextContains("DRAFT", substring = true)
        rule.onNodeWithTag("welcome_accept_checkbox").assertIsOff()
        rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
        rule.onNodeWithTag("welcome_privacy").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_document_text").assertTextContains("privacy-2026-10-DRAFT-6", substring = true)
        rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().assertIsDisplayed().assertIsOff()
        rule.onNodeWithTag("welcome_continue").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        rule.runOnIdle { assertEquals(LocalePreferences.supportedLanguages.indexOf(selected), attempts) }
        rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_continue").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(LocalePreferences.supportedLanguages.indexOf(selected) + 1, attempts) }
      }
    } finally {
      rule.runOnIdle { assertTrue(LocalePreferences.setSelection(context, original)) }
    }
  }
}
