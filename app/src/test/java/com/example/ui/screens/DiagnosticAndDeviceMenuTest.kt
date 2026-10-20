package com.example.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.model.DiagnosticTestResult
import com.example.data.model.DeviceMetric
import com.example.data.model.NextDnsProfile
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w320dp-h1000dp-mdpi", application = NextDnsApp::class)
class DiagnosticAndDeviceMenuTest {
  @get:Rule val rule = createComposeRule()

  @Test fun diagnosticBannerRequiresSuccessfulMeasurementOfSelectedProfile() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    LocalePreferences.setSelection(context, "en")
    var profile by mutableStateOf<NextDnsProfile?>(NextDnsProfile("aaaaaa", "A", "fp0123456789abcdef"))
    var result by mutableStateOf(DiagnosticTestResult(status = "ok", profileId = "fp0123456789abcdef", lastTestedTime = 1_000))
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, result, false, {}, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertIsDisplayed()
    rule.runOnIdle { result = result.copy(errorMessage = "Fixture timeout") }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertDoesNotExist()
    rule.onNodeWithText(AppStrings.get(R.string.ui_97c1211397)).assertIsDisplayed()
    rule.runOnIdle { result = result.copy(errorMessage = null, profileId = "bbbbbb") }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertDoesNotExist()
    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_unverified_title)).assertIsDisplayed()
    rule.runOnIdle { profile = null }
    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_detected_title)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.ui_ebbbefad7b)).assertDoesNotExist()
  }

  @Test fun allDevicesMenuIsLocalizedWhileRealDeviceNamesRemainUnchanged() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    var language by mutableStateOf("en")
    var selected by mutableStateOf<String?>(null)
    var menu by mutableStateOf(true)
    LocalePreferences.setSelection(context, language)
    rule.setContent {
      key(language) {
        AppTheme {
          LogsHeaderControls(
            selected,
            listOf(
              DeviceMetric(id = "turkish-device", name = "Tüm cihazlar"),
              DeviceMetric(id = "device-a", name = "My Device")
            ),
            menu,
            { menu = it }, { selected = it }, false, {}, false, {}, {})
        }
      }
    }
    LocalePreferences.supportedLanguages.forEach { locale ->
      rule.runOnIdle { LocalePreferences.setSelection(context, locale); language = locale }
      rule.waitForIdle()
      rule.onAllNodesWithText(AppStrings.get(R.string.all_devices))
        .assertCountEquals(if (locale == "tr") 3 else 2)
      rule.onNodeWithText("My Device").assertIsDisplayed()
    }
    rule.runOnIdle { LocalePreferences.setSelection(context, "en"); language = "en" }
    rule.onNodeWithText("Tüm cihazlar").performClick()
    rule.onNodeWithText("Tüm cihazlar").assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.all_devices)).assertDoesNotExist()
    rule.runOnIdle { assert(selected == "turkish-device") }
  }

  @Test fun logsStatusFiltersExposeAllDocumentedCategoriesAndReturnApiValues() {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    LocalePreferences.setSelection(context, "en")
    var selected: String? = null
    rule.setContent {
      AppTheme {
        LogsStatusFilterRow(selectedStatusFilter = selected, onSelectStatus = { selected = it })
      }
    }

    rule.onNodeWithText("All statuses").assertIsDisplayed()
    rule.onNodeWithTag("logs_status_blocked").performClick()
    rule.runOnIdle { assertEquals("blocked", selected) }
    rule.onNodeWithTag("logs_status_allowed").performClick()
    rule.runOnIdle { assertEquals("allowed", selected) }
    rule.onNodeWithTag("logs_status_default").performClick()
    rule.runOnIdle { assertEquals("default", selected) }
    rule.onNodeWithTag("logs_status_error").performClick()
    rule.runOnIdle { assertEquals("error", selected) }
  }
}
