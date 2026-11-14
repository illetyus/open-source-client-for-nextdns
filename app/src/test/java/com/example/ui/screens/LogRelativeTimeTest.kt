package com.example.ui.screens

import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.NextDnsApp
import com.example.R
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.data.model.DnsLogEntry
import com.example.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Characterize output before moving timestamp parsing out of row recomposition. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class LogRelativeTimeTest {
  @get:Rule val compose = createComposeRule()
  @Before fun selectEnglish() {
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "en")
  }

  @Test fun supportedWireTimestampFormsDescribeTheSameAge() {
    val instant = Instant.now().minusSeconds(330)
    val expected = AppStrings.plural(R.plurals.minutes_ago, 5)
    val inputs = listOf(instant.toString(), instant.atOffset(ZoneOffset.ofHours(3)).toString(),
      instant.atOffset(ZoneOffset.UTC).toLocalDateTime().toString(),
      instant.epochSecond.toString(), instant.toEpochMilli().toString())
    inputs.forEach { assertEquals(it, expected, formatRelativeTime(it)) }
  }

  @Test fun unchangedTimestampStillUsesTheNewDisplayLanguage() {
    val timestamp = Instant.now().minusSeconds(330).toString()
    val english = formatRelativeTime(timestamp)
    LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "tr")
    val turkish = formatRelativeTime(timestamp)
    assertEquals(AppStrings.plural(R.plurals.minutes_ago, 5), turkish)
    assertNotEquals(english, turkish)
  }

  @Test fun unrecognizedDisplayTextIsPreserved() {
    assertEquals("unknown timestamp", formatRelativeTime("unknown timestamp"))
  }

  @Test fun blankAndFutureTimesRetainTheCurrentNowLabel() {
    val expected = AppStrings.get(R.string.ui_68af8fa298)
    assertEquals(expected, formatRelativeTime(""))
    assertEquals(expected, formatRelativeTime(Instant.now().plusSeconds(600).toString()))
  }

  @Test fun retainedRowRecomposesItsAgeInTheNewLanguage() {
    val log = DnsLogEntry(domain = "fixture.invalid", timestamp = Instant.now().minusSeconds(9_000).toString())
    var expanded by mutableStateOf(false)
    compose.setContent {
      AppTheme {
        LogItemRow(log = log, isExpanded = expanded, onToggleExpand = {},
          onAddToAllowlist = {}, onAddToDenylist = {})
      }
    }
    compose.onNodeWithText(AppStrings.plural(R.plurals.hours_ago, 2)).assertIsDisplayed()
    compose.runOnIdle {
      LocalePreferences.setSelection(ApplicationProvider.getApplicationContext<NextDnsApp>(), "tr")
      expanded = true
    }
    compose.onNodeWithText(AppStrings.plural(R.plurals.hours_ago, 2)).assertIsDisplayed()
  }
}
