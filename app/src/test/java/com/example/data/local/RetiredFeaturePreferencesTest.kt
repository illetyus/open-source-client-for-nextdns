package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.preferences.ThemePreferences
import com.example.i18n.LocalePreferences
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class RetiredFeaturePreferencesTest {
  @Test
  fun retiredSpeedSettingsAreClearedWithoutTouchingCredentialsThemeLocaleOrLegalAcceptance() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    val retiredSettings = context.getSharedPreferences(
      "speed_test_local_settings",
      Context.MODE_PRIVATE
    )
    retiredSettings.edit()
      .putString("server_base_url", "https://speed.example")
      .putString("server_privacy_policy_url", "https://speed.example/privacy")
      .putString("server_backend_path", "backend")
      .commit()

    val credentials = context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE)
    credentials.edit().putString("saved_api_key_encrypted_v1", "credential-test-sentinel").commit()
    val theme = ThemePreferences(context)
    theme.setThemeMode(ThemeMode.DARK)
    LocalePreferences.setSelection(context, "fr")
    val legalAcceptance = LegalAcceptanceStore(context)
    assertTrue(legalAcceptance.acceptCurrentTerms())

    assertTrue(clearRetiredSpeedTestSettings(context))

    assertTrue(retiredSettings.all.isEmpty())
    assertEquals("credential-test-sentinel", credentials.getString("saved_api_key_encrypted_v1", null))
    assertEquals(ThemeMode.DARK, theme.themeMode.first())
    assertEquals("fr", LocalePreferences.selection(context))
    assertTrue(legalAcceptance.isAccepted())
  }

  @Test
  fun retiredSpeedSettingsCleanupIsIdempotent() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<NextDnsApp>()
    assertTrue(clearRetiredSpeedTestSettings(context))
    assertTrue(clearRetiredSpeedTestSettings(context))
    assertTrue(
      context.getSharedPreferences("speed_test_local_settings", Context.MODE_PRIVATE).all.isEmpty()
    )
  }
}
