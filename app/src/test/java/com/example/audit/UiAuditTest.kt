package com.example.audit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.api.SecurityFieldDiscovery
import com.example.data.api.SecurityJsonValueType
import com.example.data.api.UnverifiedSecurityField
import com.example.data.model.DiagnosticTestResult
import com.example.data.model.DnsLogEntry
import com.example.data.model.DeviceMetric
import com.example.data.model.LogsHistoryFilter
import com.example.data.model.LogsHistoryPreset
import com.example.data.model.LogsSort
import com.example.data.model.LogsTimeWindow
import com.example.data.model.NextDnsProfile
import com.example.data.repository.LogsPaginationState
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.AllowlistScreen
import com.example.ui.screens.ConnectionStatusBanner
import com.example.ui.screens.DenylistScreen
import com.example.ui.screens.EndpointsSection
import com.example.ui.screens.LinkedIpSection
import com.example.ui.screens.LogsHeaderControls
import com.example.ui.screens.LogsHistoryControls
import com.example.ui.screens.LogItemRow
import com.example.ui.screens.LogsPaginationFooter
import com.example.ui.screens.UnverifiedSecurityFieldsSection
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
  @get:Rule val rule = AuditComposeTestRule()
  private fun english() { LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en") }

  @Test fun unsafeApiProfileDoesNotExposeSetupAddressesOrInstallCommands() {
    english()
    val unsafeId = "abc123; printf P08_MARKER; #"
    response = { json("""{"data":[{"id":"$unsafeId","name":"Synthetic malformed profile"}]}""") }
    kotlinx.coroutines.runBlocking {
      org.junit.Assert.assertTrue(repository.refreshProfilesFromApi())
    }
    repository.setActiveProfile(unsafeId)
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { com.example.ui.screens.SetupScreen(model) } }
    rule.onNodeWithText(AppStrings.get(R.string.setup_profile_identity_unavailable)).assertIsDisplayed()
    rule.onAllNodes(hasText(unsafeId, substring = true)).assertCountEquals(0)
    rule.onAllNodes(hasText("brew install", substring = true)).assertCountEquals(0)
  }

  @Test fun identicalRealisticFingerprintMustShowConnectedForSelectedProfile() {
    english()
    val profile = NextDnsProfile("aaaaaa", "Synthetic A", "fp0123456789abcdef")
    val measurement = DiagnosticTestResult(status = "ok", profileId = profile.fingerprint, lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, measurement, false, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertIsDisplayed()
  }

  @Test fun detectedNextDnsWithoutSelectedProfileMustNotSayNotConfigured() {
    english()
    val measurement = DiagnosticTestResult(status = "ok", profileId = "fp0123456789abcdef", lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(null, measurement, false, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_detected_title)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.ui_1866bc156e)).assertDoesNotExist()
  }

  @Test fun unrelatedFingerprintMustNotShowHealthySelectedProfile() {
    english()
    val profile = NextDnsProfile("aaaaaa", "Synthetic A", "fp0123456789abcdef")
    val measurement = DiagnosticTestResult(status = "ok", profileId = "fpfedcba9876543210", lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, measurement, false, {}) } }
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
    org.junit.Assert.assertTrue(requests.none { it.startsWith("POST ") })
  }

  @Test fun endpointsMustUseTheValidatedProfileIpv6Format() {
    english()
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    rule.setContent { AppTheme { EndpointsSection("abcdef", context) } }
    rule.onNodeWithText("2a07:a8c0::ab:cdef\n2a07:a8c1::ab:cdef").assertIsDisplayed()
    rule.onNodeWithText("2a07:a8c0::abcdef\n2a07:a8c1::abcdef").assertDoesNotExist()
  }

  @Test fun linkedIpLabelUsesServiceBindingNotDiagnosticClientIp() {
    english()
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    rule.setContent {
      AppTheme {
        LinkedIpSection(
          diagnosticIp = "198.51.100.42",
          context = context,
          onOpenNextDnsDashboard = {}
        )
      }
    }
    rule.onNodeWithText(AppStrings.get(R.string.ui_e1ba21dcd4)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.setup_profile_binding)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.setup_linked_ip_unavailable)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.setup_detected_public_ip)).assertIsDisplayed()
    rule.onNodeWithText("198.51.100.42").assertIsDisplayed()
    rule.onNodeWithContentDescription(AppStrings.get(R.string.ui_fb8d508c13)).assertDoesNotExist()
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
  @Test fun missingFingerprintMustNotVerifyTheSelectedProfile() {
    english()
    val profile = NextDnsProfile("aaaaaa", "Synthetic A")
    val measurement = DiagnosticTestResult(status = "ok", profileId = "aaaaaa", lastTestedTime = 1_000)
    rule.setContent { AppTheme { ConnectionStatusBanner(profile, measurement, false, {}) } }
    rule.onNodeWithText(AppStrings.get(R.string.ui_934b24bdc9)).assertDoesNotExist()
    rule.onNodeWithText(AppStrings.get(R.string.diagnostic_unverified_title)).assertIsDisplayed()
  }

  @Test fun copiedIpv6MustMatchBothDisplayedServiceAddresses() {
    english()
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val expected = "2a07:a8c0::ab:cdef\n2a07:a8c1::ab:cdef"
    rule.setContent { AppTheme { EndpointsSection("abcdef", context) } }
    rule.onNodeWithText(expected).performClick()
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    org.junit.Assert.assertEquals(expected, clipboard.primaryClip!!.getItemAt(0).text.toString())
  }

  @Test fun logDeviceOptionsReturnTheNextDnsDeviceId() {
    english()
    var selected: String? = null
    rule.setContent {
      AppTheme {
        LogsHeaderControls(
          selectedDeviceFilter = null,
          detectedDevices = listOf(
            DeviceMetric(id = "device-a", name = "Annem"),
            DeviceMetric(id = "__UNIDENTIFIED__", name = AppStrings.get(R.string.logs_unidentified_device))
          ),
          showDeviceMenu = true,
          onToggleDeviceMenu = {},
          onSelectDevice = { selected = it },
          isLiveStreaming = false,
          onToggleLiveStream = {},
          showLiveStreamInfo = false,
          onToggleLiveStreamInfo = {},
          onRefreshLogs = {}
        )
      }
    }

    rule.onNodeWithTag("logs_device_option_device-a").performClick()
    rule.runOnIdle { org.junit.Assert.assertEquals("device-a", selected) }
  }

  @Test fun logHistoryControlsSelectDateRangeAndSortOrder() {
    english()
    val selected = mutableStateOf(LogsHistoryFilter())
    rule.setContent {
      AppTheme {
        LogsHistoryControls(
          selectedFilter = selected.value,
          onSelectFilter = { selected.value = it }
        )
      }
    }

    rule.onNodeWithTag("logs_history_range_button").performClick()
    rule.onNodeWithTag("logs_history_option_24h").performClick()
    rule.runOnIdle {
      org.junit.Assert.assertEquals(LogsHistoryPreset.LAST_24_HOURS, selected.value.preset)
      org.junit.Assert.assertEquals(24L, java.time.Duration.between(
        selected.value.window.fromInclusive!!,
        selected.value.window.toExclusive!!
      ).toHours())
    }

    rule.onNodeWithTag("logs_history_sort_toggle").performClick()
    rule.runOnIdle { org.junit.Assert.assertEquals(LogsSort.OLDEST_FIRST, selected.value.sort) }
  }

  @Test fun customHistoryRangeRequiresBothDatesBeforeApply() {
    english()
    val selected = mutableStateOf(LogsHistoryFilter())
    rule.setContent {
      AppTheme {
        LogsHistoryControls(
          selectedFilter = selected.value,
          onSelectFilter = { selected.value = it }
        )
      }
    }

    rule.onNodeWithTag("logs_history_range_button").performClick()
    rule.onNodeWithTag("logs_history_option_custom").performClick()
    rule.onNodeWithText(AppStrings.get(R.string.logs_history_choose_range)).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.logs_history_apply)).assertIsNotEnabled()
    rule.runOnIdle { org.junit.Assert.assertEquals(LogsHistoryPreset.AVAILABLE, selected.value.preset) }
  }

  @Test fun selectedCustomHistoryRangeIsVisibleInTheRangeButton() {
    english()
    val zone = java.time.ZoneId.systemDefault()
    val selected = LogsHistoryFilter(
      window = LogsTimeWindow.fromLocalDates(
        java.time.LocalDate.parse("2026-02-01"),
        java.time.LocalDate.parse("2026-02-03"),
        zone
      )!!,
      preset = LogsHistoryPreset.CUSTOM
    )
    rule.setContent { AppTheme { LogsHistoryControls(selectedFilter = selected, onSelectFilter = {}) } }

    val formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
      .withLocale(java.util.Locale.forLanguageTag("en"))
    val expected = AppStrings.get(
      R.string.logs_history_selected_range,
      formatter.format(selected.window.fromInclusive!!.atZone(zone).toLocalDate()),
      formatter.format(selected.window.toExclusive!!.minusNanos(1).atZone(zone).toLocalDate())
    )
    rule.onNodeWithTag("logs_history_range_button").assertTextContains(expected)
  }

  @Test fun olderLogActionIsVisibleAndLoadsTheNextPage() {
    var requested = false
    rule.setContent {
      AppTheme {
        LogsPaginationFooter(
          state = LogsPaginationState(hasMore = true),
          onLoadMore = { requested = true }
        )
      }
    }

    rule.onNodeWithTag("logs_load_older").performClick()
    rule.runOnIdle { org.junit.Assert.assertTrue(requested) }
  }

  @Test fun logRowsShowNextDnsFaviconsForBrowsingHistory() {
    english()
    rule.setContent {
      AppTheme {
        LogItemRow(
          log = DnsLogEntry(domain = "private-query.synthetic.example"),
          isExpanded = false,
          onToggleExpand = {},
          onAddToAllowlist = {},
          onAddToDenylist = {}
        )
      }
    }

    rule.onNodeWithTag("remote_favicon_image", useUnmergedTree = true).assertExists()
    rule.onAllNodesWithTag("logs_local_domain_icon", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test fun unverifiedSecurityFieldsAreDisclosedReadOnlyWithoutShowingRawValues() {
    english()
    val discovery = SecurityFieldDiscovery(
      fields = listOf(
        UnverifiedSecurityField("previewToggle", SecurityJsonValueType.BOOLEAN, true),
        UnverifiedSecurityField("previewText", SecurityJsonValueType.STRING)
      )
    )
    rule.setContent { AppTheme { UnverifiedSecurityFieldsSection(discovery) } }

    rule.onNodeWithText("previewToggle").assertIsDisplayed()
    rule.onNodeWithText("previewText").assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.security_unverified_boolean, "true")).assertIsDisplayed()
    rule.onNodeWithText(AppStrings.get(R.string.security_unverified_text)).assertIsDisplayed()
    rule.onNodeWithText("unlogged-sensitive-value").assertDoesNotExist()
  }

  @Test fun successfulAllowlistSubmissionClearsOnlyTheConfirmedText() { checkSuccessfulInput(true, false) }
  @Test fun successfulDenylistSubmissionClearsOnlyTheConfirmedText() { checkSuccessfulInput(false, false) }
  @Test fun lateAllowlistSuccessMustPreserveNewText() { checkSuccessfulInput(true, true) }
  @Test fun lateDenylistSuccessMustPreserveNewText() { checkSuccessfulInput(false, true) }

  private fun checkSuccessfulInput(allow: Boolean, editDuringRequest: Boolean) {
    english()
    val entered = java.util.concurrent.CountDownLatch(1)
    val release = java.util.concurrent.CountDownLatch(1).also { gates += it }
    response = { request ->
      if (request.method == "POST") {
        entered.countDown()
        check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
        json("{}")
      } else json("""{"data":[{"id":"confirmed.example","active":true}]}""")
    }
    val model = NextDnsViewModel(repository)
    rule.setContent { AppTheme { if (allow) AllowlistScreen(model) else DenylistScreen(model) } }
    rule.onNode(hasSetTextAction()).performTextInput("confirmed.example")
    rule.onNodeWithText(AppStrings.get(R.string.ui_422579fc64)).performClick()
    org.junit.Assert.assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
    if (editDuringRequest) rule.onNode(hasSetTextAction()).performTextReplacement("new.example")
    release.countDown()
    rule.waitUntil(10_000) { model.uiMessage.value?.isError == false }
    rule.onNode(hasSetTextAction()).assertTextContains(if (editDuringRequest) "new.example" else "")
    if (!editDuringRequest) org.junit.Assert.assertEquals("", rule.onNode(hasSetTextAction())
      .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
  }

}
