package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.legal.LegalDocuments
import com.example.i18n.LocalePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable device installation only: no real credentials or account mutation. */
@RunWith(AndroidJUnit4::class)
class NativeLegalViewportTest {
  @get:Rule val rule = createEmptyComposeRule()

  private fun revisionOf(document: String): String =
    Regex("(?m)^Revision: (.+)$").find(document)?.groupValues?.get(1)
      ?: error("Bundled legal document is missing its revision")

  @Test fun fiveLanguagesAtLargeFontRemainReadableAcrossPortraitAndLandscape() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val originalLanguage = LocalePreferences.selection(context)
    val originalFontScale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
    fun setFontScale(scale: Float) {
      instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale").use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
      }
    }
    try {
      setFontScale(2f)
      assertTrue(context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit())
      assertTrue(context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit())
      for (language in LocalePreferences.supportedLanguages) {
        assertTrue(LocalePreferences.setSelection(context, language))
        val documents = LegalDocuments.load(context, language).getOrThrow()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
          for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
            scenario.onActivity { it.requestedOrientation = orientation }
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
              Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            rule.waitUntil(20_000) {
              var ready = false
              scenario.onActivity {
                ready = it.resources.configuration.orientation == expected && it.resources.configuration.fontScale >= 1.9f
              }
              ready && rule.onAllNodesWithTag("welcome_document_viewport").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(language, LocalePreferences.resolvedLanguage(context))
            rule.onNodeWithTag("welcome_document_viewport").assertHeightIsAtLeast(160.dp).performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("welcome_document_text").assertTextContains("DRAFT", substring = true)
            rule.onNodeWithTag("welcome_privacy").performScrollTo().performClick()
            rule.onNodeWithTag("welcome_document_viewport").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("welcome_document_text").assertTextContains(revisionOf(documents.privacy), substring = true)
            rule.onNodeWithTag("welcome_terms").performScrollTo().performClick()
            rule.onNodeWithTag("welcome_document_text").assertTextContains(revisionOf(documents.terms), substring = true)
            rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().assertIsDisplayed().assertIsOff()
            rule.onNodeWithTag("welcome_continue").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            rule.runOnIdle { assertFalse(LegalAcceptanceStore(context).isAccepted()) }
          }
        }
      }
    } finally {
      setFontScale(originalFontScale)
      assertTrue(LocalePreferences.setSelection(context, originalLanguage))
    }
  }
}
