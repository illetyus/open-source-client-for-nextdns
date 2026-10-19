package com.example.audit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.model.DiagnosticTestResult
import com.example.data.model.NextDnsProfile
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.AllowlistScreen
import com.example.ui.screens.ConnectionStatusBanner
import com.example.ui.screens.DenylistScreen
import com.example.ui.screens.EndpointsSection
import com.example.data.api.SetupDto
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NextDnsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h900dp-mdpi", application = NextDnsApp::class)
class UiAuditTest : AuditFixture() {
  @get:Rule val rule = createComposeRule()
  private fun english() { LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en") }

  @Test fun identicalRealisticFingerprintMustShowConnectedForSelectedProfile() {
    english()
    val profile = NextDnsProfile("aaaaaa", "Synthetic A", "fp0123456789abcdef")
    val measurement = DiagnosticTestResult(status = "ok", profileId = profile.fingerprint, lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, measurement, false, {}, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertIsDisplayed()
  }

  @Test fun detectedNextDnsWithoutSelectedProfileMustNotSayNotConfigured() {
    english()
    val measurement = DiagnosticTestResult(status = "ok", profileId = "fp0123456789abcdef", lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(null, measurement, false, {}, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_detected_title)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.ui_1866bc156e)).assertDoesNotExist()
  }

  @Test fun unrelatedFingerprintMustNotShowHealthySelectedProfile() {
    english()
    val profile = NextDnsProfile("aaaaaa", "Synthetic A", "fp0123456789abcdef")
    val measurement = DiagnosticTestResult(status = "ok", profileId = "fpfedcba9876543210", lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, measurement, false, {}, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertDoesNotExist()
  }

  @Test fun failedAllowlistSubmissionMustPreserveTypedDomain() { checkFailedDomainInput(true) }
  @Test fun failedDenylistSubmissionMustPreserveTypedDomain() { checkFailedDomainInput(false) }

  @Test fun validPunycodeMustReachTheRepository() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { AllowlistScreen(model) } }
    rule.onNode(hasSetTextAction()).performTextInput("xn--e1afmkfd.xn--p1ai")
    rule.onNodeWithText(AppStrings.get(R.string.ui_422579fc64)).performClick()
    rule.waitUntil(5_000) { requests.any { it.startsWith("POST ") } }
  }

  @Test fun malformedLabelsMustNotReachTheRepository() {
    english()
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { DenylistScreen(model) } }
    rule.onNode(hasSetTextAction()).performTextInput("a..com")
    rule.onNodeWithText(AppStrings.get(R.string.ui_422579fc64)).performClick()
    rule.onNodeWithText(AppStrings.get(R.string.ui_585a0d389f)).assertIsDisplayed()
    assert(requests.none { it.startsWith("POST ") })
  }

  @Test fun endpointsMustUseServiceIpv6InsteadOfInvalidProfileConcatenation() {
    english()
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    rule.setContent { AppTheme { EndpointsSection("abcdef", context,
      setup = SetupDto(ipv6 = listOf("2a07:a8c0::ab:cdef", "2a07:a8c1::ab:cdef"))) } }
    rule.onNodeWithText("2a07:a8c0::ab:cdef\n2a07:a8c1::ab:cdef").assertIsDisplayed()
    rule.onNodeWithText("2a07:a8c0::abcdef\n2a07:a8c1::abcdef").assertDoesNotExist()
  }

  private fun checkFailedDomainInput(allow: Boolean) {
    english()
    response = { json("""{"errors":[{"code":"serverError"}]}""", 500) }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { if (allow) AllowlistScreen(model) else DenylistScreen(model) } }
    rule.onNode(hasSetTextAction()).performTextInput("keep.synthetic.example")
    rule.onNodeWithText(AppStrings.get(R.string.ui_422579fc64)).performClick()
    rule.waitUntil(10_000) { model.uiMessage.value?.isError == true }
    rule.onNode(hasSetTextAction()).assertTextContains("keep.synthetic.example")
  }
}
